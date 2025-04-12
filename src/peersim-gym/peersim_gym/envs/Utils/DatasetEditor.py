import json
import numpy as np

def loadDataset(file):
    """
    Loads and returns the dataset from a JSON file.
    """
    with open(file, 'r') as f:
        dataset = json.load(f)
    return dataset

def getAvailableVars(dataset):
    """
    Returns and prints a list of variable names available in the dataset.
    """
    if not dataset:
        print("Dataset is empty.")
        return []

    sample_job = next(iter(dataset.values()))
    vars_list = list(sample_job.keys())
    print("Available variables:", vars_list)
    return vars_list

def getMinAndMaxVARS(dataset, vars):
    """
    Returns a dictionary of {var: (min, max)} for a list of variables.
    """
    stats = {}
    for var in vars:
        values = [features[var] for features in dataset.values() if var in features]
        if values:
            min_val = np.min(values)
            max_val = np.max(values)
            stats[var] = (min_val, max_val)
        else:
            stats[var] = (None, None)
    return stats

def getMeanAndStdVARS(dataset, vars):
    """
    Returns a dictionary of {var: (mean, std)} for a list of variables.
    """
    stats = {}
    for var in vars:
        values = [features[var] for features in dataset.values() if var in features]
        if values:
            mean = np.mean(values)
            std = np.std(values)
            stats[var] = (mean, std)
        else:
            stats[var] = (None, None)
    return stats

def getAggregateMetrics(dataset):
    """
    Prints mean, std, min, and max for all available variables in the dataset.
    """
    aggr_data = {}
    vars = getAvailableVars(dataset)
    for var in vars:
        values = [features[var] for features in dataset.values() if var in features]
        if values:
            mean = np.mean(values)
            std = np.std(values)
            min_val = np.min(values)
            max_val = np.max(values)
            aggr_data[var] = {
                "mean": mean,
                "std": std,
                "min": min_val,
                "max": max_val
            }
            print(f"{var}: Mean = {mean:.4f}, Std = {std:.4f}, Min = {min_val:.4f}, Max = {max_val:.4f}")
        else:
            print(f"{var}: No data available.")
    return aggr_data

def printJustTheImportantMetrics(dataset):
    """
    Prints the most important metrics for the dataset.  max_memory, max_cpu, total_resources_duration
    :param dataset:
    :return:
    """
    vars = ["max_memory", "max_cpu", "total_resources_duration"]
    for var in vars:
        values = [features[var] for features in dataset.values() if var in features]
        if values:
            mean = np.mean(values)
            std = np.std(values)
            min_val = np.min(values)
            max_val = np.max(values)
            print(f"{var}: Mean = {mean:.4f}, Std = {std:.4f}, Min = {min_val:.4f}, Max = {max_val:.4f}")
        else:
            print(f"{var}: No data available.")
def saveDataset(dataset, path):
    """
    Saves the given dataset to a JSON file at the specified path.
    """
    with open(path, 'w') as f:
        json.dump(dataset, f, indent=2)
    print(f"Dataset saved to {path}")



def standerdizedDataset(dataset):
    """
    Creates and returns a new dataset where all numerical variables are normalized
    per variable, across all jobs.
    """
    # Get list of all variable names
    vars = getAvailableVars(dataset)

    # Compute mean and std for each variable
    stats = getMeanAndStdVARS(dataset, vars)

    # Build normalized dataset
    standerdized_dataset = {}
    for job_id, job in dataset.items():
        standerdized_job = {}
        for var in vars:
            if var in job:
                mean, std = stats[var]
                if std and std != 0:
                    normalized_value = (job[var] - mean) / std
                else:
                    normalized_value = 0.0  # Avoid division by zero
                standerdized_job[var] = normalized_value
        standerdized_dataset[job_id] = standerdized_job

    return standerdized_dataset

def normalizeDataset(dataset):
    vars = getAvailableVars(dataset)

    # Compute mean and std for each variable
    stats = getMinAndMaxVARS(dataset, vars)
    # Build normalized dataset
    normalized_dataset = {}
    for job_id, job in dataset.items():
        normalized_job = {}
        for var in vars:
            if var in job:
                min_val, max_val = stats[var]
                if max_val and min_val != 0:
                    normalized_value = (job[var] - min_val) / (max_val - min_val)
                else:
                    normalized_value = 0.0  # Avoid division by zero
                normalized_job[var] = normalized_value
        normalized_dataset[job_id] = normalized_job
    return normalized_dataset

def rescaleDatasetWithMeansAndStds(dataset, aggregate_metrics):
    """
    Returns a new dataset where each variable is rescaled using provided mean and std values.
    Format of stats: {var_name: {"mean": ..., "std": ...}}
    """
    rescaled_dataset = {}

    for job_id, job in dataset.items():
        rescaled_job = {}
        for var, value in job.items():
            if var in aggregate_metrics:
                mean = aggregate_metrics[var].get("mean", 0.0)
                std = aggregate_metrics[var].get("std", 1.0)
                if std != 0:
                    rescaled_value = (value * std) + mean
                else:
                    rescaled_value = 0.0  # Avoid division by zero
                rescaled_job[var] = rescaled_value
        rescaled_dataset[job_id] = rescaled_job

    return rescaled_dataset


import numpy as np
from scipy import stats

def getAggregateInstructionAndMemoryMetrics(dataset, defaultCPUWorkload, defaultMemoryWorkload):
    """
    Computes aggregate metrics (mean, std, min, max, median, mode) for:
    - estimated_instructions
    - estimated_memory
    using the formulas provided and constant workloads.

    Returns a dictionary with metrics.
    """
    instruction_list = []
    memory_list = []

    for job in dataset.values():
        max_cpu = job.get("max_cpu", 0.0)
        duration = job.get("total_resources_duration", 0.0)
        max_mem = job.get("max_mem", 0.0)

        est_instructions = (max_cpu / 100.0) * (duration / 1000.0) * defaultCPUWorkload
        est_memory = max_mem * defaultMemoryWorkload

        instruction_list.append(est_instructions)
        memory_list.append(est_memory)

    def compute_metrics(values):
        values_array = np.array(values)
        mode_result = stats.mode(values_array, nan_policy='omit')
        return {
            "mean": np.mean(values_array),
            "std": np.std(values_array),
            "min": np.min(values_array),
            "max": np.max(values_array),
            "median": np.median(values_array),
            "mode": mode_result.mode if mode_result.count > 0 else None
        }

    return {
        "estimated_instructions": compute_metrics(instruction_list),
        "estimated_memory": compute_metrics(memory_list)
    }


def rescale(dataset, scale, rescale_vars=None):
    """
    Rescales the dataset by a given scale factor.
    """
    if rescale_vars is None:
        rescale_vars = getAvailableVars(dataset)
    rescaled_dataset = {}
    for job_id, job in dataset.items():
        rescaled_job = {}
        for var, value in job.items():
                if var in rescale_vars:
                    rescaled_job[var] = value * scale
                else:
                    rescaled_job[var] = value
        rescaled_dataset[job_id] = rescaled_job
    return rescaled_dataset