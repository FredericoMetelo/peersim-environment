from setuptools import setup

setup(
    name="peersim_gym",
    version="0.0.1",
    install_requires=["gym==0.26.1", "requests==2.28.1"],
    package_dir={'peersim-gym': 'src/peersim-gym'},
    package_data={'peersim-gym': ['peersim_gym/envs/configs/*.txt', 'peersim_gym/envs/Environment/*']}
)