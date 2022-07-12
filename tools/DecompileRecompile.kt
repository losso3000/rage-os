import java.io.*
import java.nio.charset.StandardCharsets

fun main() {
    val from = File("dumps/logicos-script.txt")
    val to   = File("dumps/wtest")

    // read binaries from dumps

    val path = "dumps"
    val colorscriptBin = File("$path/colorscript.bin").readBytes()
    val bytecodes = File("$path/bytecodes.bin").readBytes()
    val constantsBin = File("$path/constants.bin").readBytes()
    val constants = readConstants(constantsBin)
    val colorscript = decompileColorscript(colorscriptBin)
    val state = DecompileState(bytecodes, constants, colorscript)
    decompileBytecode(state)

    // write logicos-script.txt

    PrintWriter(from).use { writeScript(state, it) }

    // compare exported binary for good measure

    val cmpCols = ByteArrayOutputStream()
    val cmpByte = ByteArrayOutputStream()
    val cmpCons = ByteArrayOutputStream()
    cmpCols.use { state.writeColorscript(it) }
    cmpByte.use { state.writeBytecodes(it) }
    cmpCons.use { state.writeConstants(it) }
    compare("colorscript.bin", colorscriptBin, cmpCols.toByteArray())
    compare("constants.bin  ", constantsBin, cmpCons.toByteArray())
    compare("bytecodes.bin  ", bytecodes, cmpByte.toByteArray())


    // read logicos-script.txt

    val colorBytes = mutableListOf<Int>()
    val constBytes = mutableListOf<Int>()
    val scriptBytes = mutableListOf<Int>()
    val procNameMapping = mutableMapOf<String, String>()
    val lines = from.readLines()
    loadScript(
        lines,
        colorBytes,
        constBytes,
        scriptBytes,
        procNameMapping
    )

    // read binaries from script

    val cols2 = colorBytes.map { it.toByte() }.toByteArray()
    val bytes2 = scriptBytes.map { it.toByte() }.toByteArray()
    val consts2 = constBytes.map { it.toByte() }.toByteArray()
    val tconstants = readConstants(consts2)

    require(tconstants.size > 100) { " too few constants?" }

    val tcolorscript = decompileColorscript(cols2)
    val tstate = DecompileState(bytes2, tconstants, tcolorscript)


    decompileBytecode(tstate)

    // write wtest

    PrintWriter(to).use { writeScript(tstate, it) }

    val srcLines = from.readLines(StandardCharsets.US_ASCII)
    val dstLines = to.readLines(StandardCharsets.US_ASCII)

    for (i in srcLines.indices) {
        val A = srcLines.getOrNull(i)?.trim()
        val B = dstLines.getOrNull(i)?.trim()
        if (A == B) {
            println("%4d OK  :   %-25s | %s".format(i, A, B))
            continue
        }
        println("%4d DIFF:   %-25s | %s".format(i, A, B))
        for (tail in 1..5) {
            println("%4d DIFF:   %-25s | %s".format(i, srcLines.getOrNull(i+tail)?.trim()?:"", dstLines.getOrNull(i+tail)?.trim()?:""))
        }

        return
    }
}

fun compare(what: String, a: ByteArray, b: ByteArray) {
    println("%s  original %8s  exported %8s -> %s".format(what, a.size, b.size, if (a.size == b.size) "OK" else "FAIL"))
    var mismatches = 0
    var aborted = false
    for (i in 0 until Math.max(a.size, b.size)) {
        if (a.getOrNull(i) != b.getOrNull(i)) {
            println("%s  DIFFER AT POS %d   orig [%02x] expo [%02x]".format(what, i,
                (a.getOrNull(i)?.toInt()?:0) and 0xff,
                (b.getOrNull(i)?.toInt()?:0) and 0xff
            ))
            if (mismatches++ > 10) {
                aborted = true
                break
            }
        }
    }
    if (mismatches > 0)
    println("%s  mismatches: %d %s".format(what, mismatches, if(aborted)"(or more)" else ""))
}

fun writeScript(state: DecompileState, w: PrintWriter, constsW: PrintWriter = w) {

    constsW.println("#")
    constsW.println("# color script")
    constsW.println("#")
    var currentFrame = -1
    state.colorscript.forEach { col ->
        if (col is ColorScriptWait) {
            val frames = -(0xffff0000 or col.originalWord.toLong()).toInt()
            constsW.printf("wait %d\n", frames)
            currentFrame += frames
        } else {
            constsW.printf("%04x\t\t# [frame %04d] %s%n", col.originalWord, currentFrame, col.representation)
        }
    }
    constsW.println()

    w.println("#")
    w.println("# bytecode")
    w.println("#")
    state.procs.forEachIndexed { index, proc ->
        w.printf("%nproc %s %s%n", proc.name, IntRange(0, proc.numArgs-1).map { Char('a'.code + it) }.joinToString(" "))
        try {
            var indent = 1
            // proc.byteCodes.forEach { w.printf("\t%s%n", it.asSourceCode()) }
            val instructions = secondPassDecompile(proc)
            annotateStackArguments(instructions, state)
            proc.callSignatures.forEach { w.printf("\t# %s%n", it) }
            instructions.forEach { instruction ->
                // poor man's ad-hoc indentation
                var thisIndent = indent
                var nextIndent = indent
                if (instruction is EncodedByte) {
                    when (instruction.byteCode) {
                        ByteCode.BC_WHEN -> nextIndent++
                        ByteCode.BC_ELSE -> thisIndent--
                        ByteCode.BC_DONE -> { thisIndent--; nextIndent--}
                    }
                }
                val sourceLine = instruction.asSourceCode()
                var commentLine = ""
                if (instruction.comments.isNotEmpty()) {
                    var commentTabs = 1
                    val sourceLen = indent * 8 + sourceLine.length
                    var linePos = sourceLen
                    val nextTabPos = (linePos - (linePos % 8)) * 8
                    if (nextTabPos < sourceLen) linePos += 8
                    while (linePos < 40) {
                        commentTabs++
                        linePos += 8
                    }
                    commentLine = "%s# %s".format("\t".repeat(commentTabs), instruction.comments.joinToString(" | "))
                }
                w.println("%s%s%s".format("\t".repeat(thisIndent), instruction.asSourceCode(), commentLine))
                indent = nextIndent
            }
        } catch (e: Exception) {
            IllegalArgumentException("Error decompiling $proc", e).printStackTrace()
            w.printf("# second pass decompilation failed, dumping bytecodes")
            proc.byteCodes.forEach { bc ->
                w.printf("\t%s%n", format(bc))
            }
        }
    }
}

fun loadScript(
    lines: List<String>,
    colorBytes: MutableList<Int>,
    constBytes: MutableList<Int>,
    scriptBytes: MutableList<Int>,
    procNameMapping: MutableMap<String, String>
) {
    var inColors = false
    var inBytecode = false
    val preliminaryConsts = mutableListOf<RoseConst>()
    val byteCodesAndUnresolvedProcs = mutableListOf<Any>()
    val procIndexByName = mutableMapOf<String, Int>()
    var procNum = 0
    lines.forEachIndexed { index, line ->
        // println("... $index : $line")
        if (line.contains("# color")) {
            println("--> in colors at line ${index+1}")
            inColors = true
            inBytecode = false
            return@forEachIndexed
        }
        if (line.contains("# bytecode")) {
            println("--> in bytecode at line ${index+1}")
            inBytecode = true
            inColors = false
            return@forEachIndexed
        }
        if (line.isBlank() || line.trim().startsWith("#")) return@forEachIndexed

        if (inColors) {
            val waitAmount = extract(line.trim(), "wait (\\d+).*")?.toInt()
            if (waitAmount != null) {
                val word = (-waitAmount) and 0xffff
                colorBytes += (word shr 8)
                colorBytes += (word and 0xff)
            } else {
                val word = line.trim().substring(0, 4).toInt(16)
                colorBytes += (word shr 8)
                colorBytes += (word and 0xff)
            }
        } else if (inBytecode) {
            val trimmed = line.substringBefore('#').trim()
            if (trimmed.startsWith("proc ")) {
                val procName = extract(trimmed, "proc (\\S+).*") ?: throw IllegalArgumentException("missing proc name in $trimmed")
                require(procIndexByName[procName] == null) { "duplicate proc: $procName" }
                procIndexByName[procName] = procIndexByName.size
                procNameMapping["proc_%03d".format(procNum++)] = procName
            } else {
                trimmed.split("::".toRegex()).forEach { subLine ->
                    byteCodesAndUnresolvedProcs.addAll(compileDecompiledLine(subLine.trim(), preliminaryConsts))
                }
            }
        } else {
            println("Warn: unexpected script input in line ${index+1}: $line")
        }
    }
    // write constant pool
    preliminaryConsts.forEach { const ->
        constBytes += (const.i shr 8) and 0xff
        constBytes += (const.i shr 0) and 0xff
        constBytes += (const.frac shr 8) and 0xff
        constBytes += (const.frac shr 0) and 0xff
    }
    println("constant pool size: ${preliminaryConsts.size}")
    require(preliminaryConsts.size < BIG_CONSTANT_BASE + 255) { "constant pool overflow" }
    // resolve proc name
    byteCodesAndUnresolvedProcs.forEach { elem ->
        if (elem is Int) scriptBytes += elem
        else if (elem is ProcNumberOfProcNameReference) scriptBytes += procIndexByName[elem.name] ?: throw IllegalArgumentException("cannot resolve proc ${elem.name}")
    }
}

fun format(byte: EncodedByte) = "%02x %-22s %s".format(byte.originalByte, byte, byte.argument ?: "")


