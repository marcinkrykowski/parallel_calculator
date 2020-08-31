# Parallel Calculator
## Purpose
Repository created to keep track of requested task implementation.

## Problem:
For problem requirements check [description](Task3-ParallerCalculator.pdf).

## Solution:
Calculator runs on Akka HTTP server. String input is parsed using Scala's parsers combinators and then evaluated by transforming Expression AST into Akka Streams graph in an attempt to use pipelining and parallelism.

## Algorithm:
Flow is as follows:
1. Consider all expressions with the highest-level nesting of parentheses in parallel
2. Change division to multiplication and perform them all in parallel
3. Change subtraction to addition and perform them all in parallel
4. Remove meaningless parentheses and if expression is not a number, go back to step 1.