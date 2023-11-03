#!/bin/bash
r=$(lsof -i :8080 | grep -oP '\s+\K\d+\s+' |  head -n 1)
read -p "Do you wish to kill $r (ENTER & y -> kill | anything else -> no) ?" -r input

if [[ "$input" == "y" || "$input" == "Y"|| "$input" == "" ]]; then
  echo "Killing $r"
  kill $r
else
  echo "Nothing was killed"
fi


# Certainly, let me explain each element of the regular expression pattern `\s+\K\d+\s+`:
#
#1. `\s+`: This part of the pattern matches one or more whitespace characters. The `\s` is a shorthand character class for whitespace, which includes spaces, tabs, and newline characters. The `+` quantifier means "one or more," so `\s+` matches one or more consecutive whitespace characters.
#
#2. `\K`: `\K` is an escape sequence in regular expressions that resets the start of the match. It's often used in lookbehinds to exclude part of the matched text from the result. In this pattern, it's used to indicate that the whitespace characters before and after the digits should not be included in the match.
#
#3. `\d+`: This part of the pattern matches one or more digits (0-9). The `\d` is a shorthand character class for digits, and the `+` quantifier means "one or more," so `\d+` matches one or more consecutive digits.
#
#4. `\s+`: Similar to the first `\s+`, this part of the pattern matches one or more whitespace characters after the digits.
#
#In summary, the pattern `\s+\K\d+\s+` matches one or more digits surrounded by whitespace characters but excludes the leading and trailing whitespace from the matched result. In the context of your Bash command, this pattern is used to extract the process ID (PID) from a string, assuming the PID is surrounded by whitespace characters.