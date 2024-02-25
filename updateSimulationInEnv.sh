#!/bin/bash
rm ./src/peersim-gym/peersim_gym/envs/Environment/*
java --version
if [[ -z "$(ls ./src/peersim-gym/peersim_gym/envs/Environment)" ]]
then
    echo "Environment files were cleaned in the environmment directory"
else
    echo "Environment files were NOT cleaned in the environmment directory"
    echo "$(ls ./src/peersim-gym/peersim_gym/envs/Environment)"
fi

mvn clean -Dmaven.test.skip package

echo "Copying files..."
cp ./target/*.jar ./src/peersim-gym/peersim_gym/envs/Environment
if [ -f ./target/*.jar ]; then
    echo ".jar file success"
else
    echo ".jar file failed"
fi

cp ./target/*.original ./src/peersim-gym/peersim_gym/envs/Environment
if [ -f ./target/*.original ]; then
    echo ".original file success"
else
    echo ".original file failed"
fi