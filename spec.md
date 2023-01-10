# Specification

## Literals, Data Types, and Variables

### Numbers
All number literals are in decimal, and all numbers are 16-bit signed integers.
This the only data type that can be stored.
Numbers should be stored in two's complement, since this is what AVC itself uses.

`RND` is a number literal that generates a random value from 0 to 255 (inclusive) that is not necessarily cryptographically secure.

### Strings
Strings are created with double quotes (`"`) and can contain line separators.
They are only used with `PRINT` and `ASM`.
Since they are not able to be stored, there is no preferred representation of strings in memory.

### Booleans
There are no boolean literals.
A boolean can only be created with a relational operator, and can only be used with `IF`.

### Labels
A label is a single lowercase letter before a command.
Labels are used with `GOTO` and `GOSUB`.

### Variables
A variable name must be a single uppercase letter and can only store a 16-bit signed integer.

## Operators
### Arithmetic
There are four arithmetic operators: `+`, `-`, `*`, and `/`, which only operate on numbers.
Addition and subtraction use wrapping arithmetic.
The overflow behavior of multiplication is undefined.

### Relational
The operators `>`, `<`, `>=`, `<=`, `==`, and `!=` create a boolean by comparing two numbers.

### Other
`=` assigns a value to a variable in a `LET` command.

## Numeric Expressions
Numeric expressions can contain variables and/or numbers separated by arithmetic operators and grouped with parentheses.

## Comments
`//` starts a comment that continues until the end of a line.

## Commands
### PRINT
`PRINT` takes a comma-separated list of numeric expressions and/or strings and prints them to stdout.
No separator is printed between each entry, but a newline is printed after the entire command.

Examples:
```
PRINT 1 + 2 // 3
PRINT "hello to the ", 4 / 2, " of you" // hello to the 2 of you
```

### IF
`IF` checks a condition and executes a statement if it is true.
The condition must be followed by the keyword `THEN`, followed by the statement.

The statement after `THEN` may have its own label.
If it does, and that label is jumped to, the condition will NOT be checked.

Example:
```
IF RND < 4 THEN a PRINT "small" // calling GOTO a does not check the condition
```

### LET
`LET` assigns a value to a variable and overrwrites an existing value.

Example:
```
LET A = 1
LET A = A + 1
PRINT A // 2
```

### INPUT
`INPUT` is yet to be defined.
`INPUT` takes a comma-separated list of variable names.
If an implementation wants to disallow all user input or is unable to support input, it should set all variables in the list to 0.

Example:
```
INPUT is not fully defined at this time.
```

### GOTO
`GOTO` jumps to a given label.

Example:
```
LET A = RND - 128
IF A > 0 GOTO a
LET A = -A
a PRINT A // GOTO jumps to here
```

### GOSUB and RETURN
`GOSUB` jumps to a given label and remembers where it was called.
`RETURN` jumps to the statement after the last `GOSUB` command.

Example:
```
GOSUB a
PRINT "done"

// define subroutine a
a PRINT "we are in a subroutine"
PRINT "let's go back now!"
RETURN
```
Output:
```
we are in a subroutine
let's go back now!
done
```

### END
`END` immediately terminates the program. This statement is implicitly inserted at the end of each program.

### ASM
`ASM` takes in a string of assembly and emits it verbatim to the output file.
There are no sanity checks for assembly, so use this with caution.
See [a2asm](https://github.com/ambyshframber/a2asm) for ASM syntax.

Example:
```
// calling assembly
ASM "LIT #0a LIT2 #ff #09 STA" // prints a newline
```

You can use this to define ASM macros that contain some or all of their code in avcbasic.
Remember that code in a macro definition is not run until the macro is invoked.

Example:
```
// defining and calling an ASM macro containing avcbasic
ASM ".defmac(printNewlineTwice, (), (" // define macro
// this code is not run until the macro is invoked
PRINT ""
PRINT ""
ASM "))" // end macro definition

ASM "%printNewlineTwice" // calls PRINT "" twice
```

#### Using avcbasic Labels in ASM
ASM code can use avcbasic labels by prepending `AVCBASIC_` to the label name.
For example, `ASM "LIT2 .absc(AVCBASIC_a) JMP2"` is equivalent to `GOTO a`.

#### Using ASM labels in avcbasic
avcbasic can use ASM labels only if they are of the form `AVCBASIC_$L`, where `$L` represents a valid avcbasic label.
For example, a label defined with `ASM ".lbl(AVCBASIC_a)"` can be jumped to with `GOTO a`.

#### Forbidden ASM labels
A compile-time error occurs if an `ASM` block defines a label that starts with `AVCBINTERNAL_`.
These labels are reserved for the compiler.

Note that *jumping* to labels with this prefix is allowed, but not encouraged,
since the compiler can change its internal labels at any time without warning.

# Formal Grammar
`number` is defined as the regex `[0-9]+`.
`string` is defined as the regex `".*?"`, where `.` matches any character including line separators.
Commas separate tokens.
```ebnf
program = statement, { statement };
statement = [label], command;
label = 'a' | 'b' | ... | 'z';
command = "PRINT", (string | expression), { ',', (string | expression) }
    | "IF", expression, relop, expression, "THEN", statement
    | ("GOTO" | "GOSUB"), label
    | "INPUT", var, { ',', var }
    | "LET", var, '=', expression
    | "RETURN" | "END"
    | "ASM", string
;
var = 'A' | 'B' | ... | 'Z';
expression = ['+' | '-'], term, { ('+' | '-'), term };
term = factor, { ('*' | '/'), factor };
factor = number | var | "RND"
    | '(', expression, ')'
;
relop = ('>' | '<'), ['='] | ('=' | '!'), ('=');
```

## Comments
Comments are defined as the regex `//.*(\r?\n|\r)` where `.` does not match line separators.
Comments cannot be contained in multiline strings.
