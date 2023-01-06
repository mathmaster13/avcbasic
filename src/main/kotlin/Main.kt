@file:JvmName("AvcBasic")
package io.github.mathmaster13.avcbasic

import java.io.File

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Please specify an input program file.")
        return
    }
    val name = args[0]
    val lexer = Lexer(File(name).readText())
    lexer.scanAll()
    val parser = Parser(lexer.tokens)
    val ast = parser.parse().evaluate()
    val output = File("$name.avc")
    output.delete()
    val writer = output.writer()
    writer.write(ast.visit())
    writer.close()
}
