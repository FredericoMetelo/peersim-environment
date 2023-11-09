Write-Output "CREATE ENV"
conda create -n PeersimGym python=3.10
Write-Output "ACTIVATE ENV"
conda activate PeersimGym
Write-Output "INSTALL PACKAGES WITH ANACONDA SOURCE IN ENV"
conda install -c anaconda pip requests matplotlib gymnasium
Write-Output "INSTALL TENSORFLOW IN ENV"
conda install -c conda-forge tensorflow
Write-Output "CONDA DEVELOP ENV"
conda develop ../src/peersim-gym -n PeersimGym
Write-Output "PIP INSTALL"
pip install pettingzoo