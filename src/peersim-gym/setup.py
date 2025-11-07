# python
from setuptools import setup, find_packages

setup(
    name="PeersimGym",
    version="0.0.2",
    packages=find_packages(where="."),
    install_requires=["gymnasium>=0.26.3", "requests>=2.28.1"],
    package_data={
        "peersim_gym": ["envs/configs/*.txt", "envs/Environment/*"]
    },
    include_package_data=True,
)
