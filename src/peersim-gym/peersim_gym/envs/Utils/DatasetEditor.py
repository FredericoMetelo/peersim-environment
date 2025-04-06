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



def createNormalizedDataset(dataset):
    """
    Creates and returns a new dataset where all numerical variables are normalized
    per variable, across all jobs.
    """
    # Get list of all variable names
    vars = getAvailableVars(dataset)

    # Compute mean and std for each variable
    stats = getMeanAndStdVARS(dataset, vars)

    # Build normalized dataset
    normalized_dataset = {}
    for job_id, job in dataset.items():
        normalized_job = {}
        for var in vars:
            if var in job:
                mean, std = stats[var]
                if std and std != 0:
                    normalized_value = (job[var] - mean) / std
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
                    rescaled_value = (value - mean) / std
                else:
                    rescaled_value = 0.0  # Avoid division by zero
                rescaled_job[var] = rescaled_value
        rescaled_dataset[job_id] = rescaled_job

    return rescaled_dataset
