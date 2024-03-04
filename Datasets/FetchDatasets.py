onedrive="https://unlpt-my.sharepoint.com/:f:/g/personal/fc_metelo_fct_unl_pt/EsXuTkMk27FEi-imb0ZczucBY09kP9faCB53ZONEYGf_bw?e=sQ4H6o"

# Code to download the one drive file
import requests

def download_file_from_onedrive(url, file_path):
    # THis is broken, fetches the actual html of the OneDrive page...
    with requests.get(url, stream=True) as r:
        r.raise_for_status()
        with open(file_path, 'wb') as f:
            for chunk in r.iter_content(chunk_size=8192):
                f.write(chunk)

if __name__ == "__main__":
    download_file_from_onedrive(onedrive, "AlibabaTraceData.csv")