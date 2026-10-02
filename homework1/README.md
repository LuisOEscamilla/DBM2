# Homework 1 - In-Memory B+ Tree Index

Single source file: `bplus.java` (Java 8 or newer).

## Compile

    javac bplus.java

## Run

    java bplus init <d> < test.txt

where `test.txt` is a file of commands. Example with d = 2:

    java bplus init 2 < test.txt

Sample command files and their expected output are in the `tests` folder, for example:

    java bplus init 2 < tests/test1.txt

## Commands (one per line in the input file)

    INSERT <key> <pointer>
    DELETE <key>
    SEARCH <key>
    RANGESEARCH <k1> <k2>
    PRINT
    PRINT STATISTICS