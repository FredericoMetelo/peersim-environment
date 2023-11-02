#!/bin/bash
rm ./src/peersim-gym/peersim_gym/envs/Environment/*
mvn clean -Dmaven.test.skip package
cp ./target/*.jar ./src/peersim-gym/peersim_gym/envs/Environment
cp ./target/*.original ./src/peersim-gym/peersim_gym/envs/Environment
