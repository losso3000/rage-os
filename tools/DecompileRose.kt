import java.io.File
import java.io.OutputStream
import kotlin.collections.ArrayDeque
import kotlin.math.roundToInt

const val END_OF_SCRIPT	  = 0xFF
const val COLORSCRIPT_END = 0x8000
const val USE_LATEST      = false
const val DEBUG_DECOMPILE = false
const val DEBUG_CONSTANTS = false
const val DEBUG_COLORSCRIPT=false
const val BIG_CONSTANT_BASE= 0x7e

// see: bytecode.h

enum class ByteCode {
    BC_DONE     (0x00, "   t",   0, "done"),
    BC_ELSE     (0x01, "  jt",   0, "else"),
    BC_END      (0x02, "    ",   0, "end"),
    BC_RAND     (0x03, " o  ",   0, "rand"),
    BC_DRAW     (0x04, "    ",   0, "draw"),
    BC_TAIL     (0x05, " o  ",   0, "tail"),
    BC_PLOT     (0x06, "    ",   0, "plot"),
    BC_PROC     (0x07, " o  ",   0, "proc"),
    BC_POP      (0x08, "i   ",   0, "pop"),
    BC_DIV      (0x09, "io  ",   0, "/"),
    BC_WAIT     (0x0A, "i   ",   0, "wait"),
    BC_SINE     (0x0B, "io  ",   0, "sine"),
    BC_SEED     (0x0C, "i   ",   0, "seed"),
    BC_NEG      (0x0D, "io  ",   0, "~"),
    BC_MOVE     (0x0E, "i   ",   0, "move"),
    BC_MUL      (0x0F, "io  ",   0, "*"),
    BC_WHEN     (0x10, "i j ",  15),
    BC_FORK     (0x20, "i   ",  15),
    BC_OP       (0x30, "io  ",  15),
    BC_WLOCAL   (0x40, "i   ",  15),
    BC_WSTATE   (0x50, "i   ",  15),
    BC_RLOCAL   (0x60, " o  ",  15),
    BC_RSTATE   (0x70, " o  ",  15),
    BC_CONST    (0x80, " o  ", 126),

    BC_BIGCONST_MARKER (BIG_CONSTANT_BASE+0x80, "", 0),            // added
    BC_BIGCONST       (-1, "", 255+BIG_CONSTANT_BASE), // value after BC_BIGCONST
    BC_PROC_REF       (-1, "", 255),                   // value after BC_PROC
    BC_END_OF_SCRIPT  (0xff, "", 0);

    val code: Int
    val hasInput: Boolean
    val hasOutput: Boolean
    val maxArgumentCode: Int
    val sourceCodeRepresentation: String?

    constructor(code: Int, notes: String, maxArgumentCode: Int, sourceCodeRepresentation: String? = null) {
        this.code = code
        this.hasInput = notes.contains('i')
        this.hasOutput = notes.contains('o')
        this.maxArgumentCode = maxArgumentCode
        this.sourceCodeRepresentation = sourceCodeRepresentation
    }

    fun hasArgument() = maxArgumentCode > 0

}

class EncodedByte : ValueWithByteRepresentation {
    val originalByte: Int
    val byteCode: ByteCode
    val argumentCode: Int
    var argument: Any?
    constructor(originalByte: Int, byteCode: ByteCode, argumentCode: Int = 0, argument: Any? = null) : super("%02x".format(originalByte)) {
        require(argumentCode in 0..byteCode.maxArgumentCode) { "illegal argument for $byteCode: $argumentCode not in 0..${byteCode.maxArgumentCode}" }
        this.originalByte = originalByte
        this.byteCode = byteCode
        this.argumentCode = argumentCode
        // some byte codes can translate the argumentCode directly
        this.argument = when (byteCode) {
            ByteCode.BC_WHEN   -> WhenOp.fromCode(argumentCode) ?: throw IllegalArgumentException("illegal WhenOp argument for $byteCode: $argumentCode")
            ByteCode.BC_OP     -> Op.fromCode(argumentCode)     ?: throw IllegalArgumentException("illegal Op argument for $byteCode: $argumentCode")
            ByteCode.BC_RLOCAL,
            ByteCode.BC_WLOCAL -> LocalVar(argumentCode)
            ByteCode.BC_RSTATE,
            ByteCode.BC_WSTATE -> EncodedStateVar(argumentCode)
            else -> argument
        }
    }

    override fun toString() = if (byteCode.hasArgument()) "$byteCode($argumentCode)" else byteCode.name
    override fun toBytes(state: DecompileState): List<EncodedByte> = listOf(this)
    override fun asSourceCode(): String {
        if (byteCode.sourceCodeRepresentation != null) return byteCode.sourceCodeRepresentation
        if (byteCode == ByteCode.BC_OP)                 return (argument as Op).symbol
        if (byteCode == ByteCode.BC_RLOCAL)             return "local.$argument"
        if (byteCode == ByteCode.BC_RSTATE)             return "state.$argument"
        if (byteCode == ByteCode.BC_WLOCAL)             return "set local.$argument"
        if (byteCode == ByteCode.BC_WSTATE)             return "set state.$argument"
        if (byteCode == ByteCode.BC_FORK)               return "fork($argumentCode)"
        if (byteCode == ByteCode.BC_WHEN)               return "when(${(argument as WhenOp).symbol})"
        if (byteCode.hasArgument() && argument != null) return "%s(%s) %s".format(byteCode, argumentCode, argument)
        if (byteCode.hasArgument())                     return "%s(%s)".format(byteCode, argumentCode)
        return byteCode.toString()
    }

    companion object {
        fun fromByte(byte: Int, previousByte: Int? = null, previousByteCode: ByteCode?): EncodedByte? {
            if (previousByte == ByteCode.BC_BIGCONST_MARKER.code) return EncodedByte(byte, ByteCode.BC_BIGCONST, byte + BIG_CONSTANT_BASE)
            if (previousByte == ByteCode.BC_PROC.code
                && previousByteCode != ByteCode.BC_PROC_REF
                && previousByteCode != ByteCode.BC_BIGCONST
            )            return EncodedByte(byte, ByteCode.BC_PROC_REF, byte)
            if (byte         == ByteCode.BC_END_OF_SCRIPT.code)   return EncodedByte(byte, ByteCode.BC_END_OF_SCRIPT)
            if (byte         == ByteCode.BC_BIGCONST_MARKER.code) return EncodedByte(byte, ByteCode.BC_BIGCONST_MARKER)
            if (byte         >= ByteCode.BC_CONST.code)           return EncodedByte(byte, ByteCode.BC_CONST, byte - ByteCode.BC_CONST.code)
            val base = byte and 0xf0
            ByteCode.values().forEach { byteCode ->
                if (!byteCode.hasArgument() && byteCode.code == byte) return EncodedByte(byte, byteCode)
                if (byteCode.hasArgument() && byteCode.code == base) return EncodedByte(byte, byteCode, byte - base)
            }
            return null
        }
    }
    override fun hasInput() = byteCode.hasInput // TODO numer of inputs from stack? (ADD=1 vs MUL=2 vs FORK(10)=10+1)
    override fun hasOutput() = byteCode.hasOutput
}

class LocalVar(val num: Int) {
    override fun toString() = argumentName(num)
}

enum class Op(val code: Int, val symbol: String) {
    OP_ASR  ( 0, ">>"),
    OP_LSR  ( 1, "<lsr>"),
    OP_ROXR ( 2, "<roxr>"),
    OP_ROR  ( 3, "<ror>"),
    OP_ASL  ( 4, "<<"),
    OP_LSL  ( 5, "<lsl>"),
    OP_ROXL ( 6, "<roxl>"),
    OP_ROL  ( 7, "><<"),
    OP_OR   ( 8, "|"),
    OP_SUB  ( 9, "-"),
    OP_CMP  (11, "<cmp>"),
    OP_AND  (12, "&"),
    OP_ADD  (13, "+");
    companion object {
        fun fromCode(code: Int) = values().filter { it.code == code }.firstOrNull()
    }
}

enum class WhenOp(val code: Int, val symbol: String) {
    CMP_EQ(   6, "=="),
    CMP_NE(   7, "!="),
    CMP_LT(  12, "<"),
    CMP_GE(  13, ">="),
    CMP_LE(  14, "<="),
    CMP_GT(  15, ">");
    companion object {
        fun fromCode(code: Int) = values().filter { it.code == code }.firstOrNull()
    }
}

enum class StateVar(val sourceCodeRepresentation: String) {
    ST_PROC("proc"),
    ST_X("x"),
    ST_Y("y"),
    ST_SIZE("size"),
    ST_TINT("tint"),
    ST_RAND("seed"),
    ST_DIR("face"),
    ST_TIME("time"),
    ST_WIRE0("global_variable_");
    companion object {
        fun fromCode(code: Int) = values().filter { it.ordinal == code }.firstOrNull()
    }
}

class EncodedStateVar {
    val code: Int
    val representation: String
    constructor(code: Int) {
        this.code = code
        if (code >= StateVar.ST_WIRE0.ordinal) {
            this.representation = globalVarName(code - StateVar.ST_WIRE0.ordinal)
        } else {
            val type = StateVar.fromCode(code)
            requireNotNull(type) { "illegal RSTATE/WSTATE code: $code" }
            this.representation = type.sourceCodeRepresentation
        }
    }

    override fun toString() = representation
}

abstract class ValueWithByteRepresentation(val representation: String) {
    override fun toString() = representation
    abstract fun toBytes(state: DecompileState): List<EncodedByte>
    abstract fun asSourceCode(): String
    abstract fun hasInput(): Boolean
    abstract fun hasOutput(): Boolean
    var comments = mutableListOf<String>()
}

fun formatNumber(value: Double): String {
    val rounded = (Math.round(value * 100.0)/100.0).toString()
    return if (rounded.endsWith(".0")) rounded.replace(".0", "") else rounded
}

class RoseConst(val index: Int, val i: Int, val frac: Int): ValueWithByteRepresentation(findBestRepresentation(i, frac)) {
    override fun toBytes(state: DecompileState): List<EncodedByte> {
        if (index < BIG_CONSTANT_BASE) {
            return listOf(
                EncodedByte(ByteCode.BC_CONST.code + index, ByteCode.BC_CONST, index, this)
            )
        } else {
            return listOf(
                EncodedByte(ByteCode.BC_BIGCONST_MARKER.code, ByteCode.BC_BIGCONST_MARKER),
                EncodedByte(index - BIG_CONSTANT_BASE, ByteCode.BC_BIGCONST, index, this)
            )
        }
    }
    override fun asSourceCode() = representation
    override fun hasInput() = false
    override fun hasOutput() = true
    fun toHex() = "%04x:%04x".format(i, frac)
}

fun findBestRepresentation(i: Int, frac: Int): String {
    if (frac == 0) return i.toString()
    val doubleValue  = i + frac / 65536.0
    val fracDecimals = formatNumber(doubleValue)
    if (fracDecimals == doubleValue.toString()) {
        return fracDecimals
    }
    return "%04x:%04x".format(i, frac)
}

fun fromConstRepresentation(s: String): RoseConst? {
    var frac: Int? = null
    var i: Int? = null
    val m1 = "(\\d+\\.\\d+)".toRegex().matchEntire(s)
    if (m1 != null) {
        val doubleValue = m1.groupValues[1].toDouble()
        i = doubleValue.toInt()
        frac = ((doubleValue - i.toDouble()) * 0x10000).roundToInt()
    } else {
        val m2 = "(\\d+)".toRegex().matchEntire(s)
        if (m2 != null) {
            i = m2.groupValues[1].toInt()
            frac = 0
        } else {
            val m3 = "([0-9a-z]{4}):([0-9a-z]{4})".toRegex().matchEntire(s)
            if (m3 != null) {
                i = m3.groupValues[1].toInt(16)
                frac = m3.groupValues[2].toInt(16)
            }
        }
    }
    if (i == null || frac == null) return null
    return RoseConst(9999, i, frac)
}

class Proc(val num: Int, var name: String, var numArgs: Int) {
    val lines = mutableListOf<String>()
    val callSignatures = mutableListOf<String>()
    val byteCodes = mutableListOf<EncodedByte>()
    fun drawCount() = byteCodes.filter { it.byteCode == ByteCode.BC_DRAW }.size
    fun plotCount() = byteCodes.filter { it.byteCode == ByteCode.BC_PLOT }.size
    fun moveCount() = byteCodes.filter { it.byteCode == ByteCode.BC_MOVE }.size
    fun resolveProcReferences(procs: MutableList<Proc>) {
        byteCodes.forEachIndexed { index, byteCode ->
            if (byteCode.byteCode == ByteCode.BC_PROC_REF) {
                val procNum = byteCode.argumentCode
                var proc = procs.getOrNull(procNum)
                requireNotNull(proc) { "cannot resolve proc_%03d in proc_%03d (%s) (max is %d)".format(procNum, num, name, procs.size-1) }
                byteCode.argument = proc
            }
        }
    }

    override fun toString() = name
}

class ProcReference(val ref: Proc): ValueWithByteRepresentation(ref.name) {
    override fun toBytes(state: DecompileState): List<EncodedByte> {
        return listOf(
            EncodedByte(ByteCode.BC_PROC.code, ByteCode.BC_PROC),
            EncodedByte(ref.num, ByteCode.BC_PROC_REF, ref.num, state.procs[ref.num]),
        )
    }
    override fun asSourceCode() = "<%s>".format(representation)
    override fun hasInput() = false
    override fun hasOutput() = true
}

class LocalVariable(val index: Int, val name: String): ValueWithByteRepresentation(name) {
    override fun toBytes(state: DecompileState): List<EncodedByte> {
        return listOf(EncodedByte(ByteCode.BC_RLOCAL.code + index, ByteCode.BC_RLOCAL, index, this))
    }

    override fun asSourceCode() = representation
    override fun hasInput() = false
    override fun hasOutput() = true
}

class DecompileState(val data: ByteArray, var constants: List<RoseConst>, val colorscript: List<ColorScriptEntry>, val renamer: (String) -> String = { it }) {
    var pos = 0
    val stack = ArrayDeque<Any>()
    val procs = mutableListOf<Proc>()
    val local = mutableMapOf<Int,Any>()
    var currentProc: Proc
    var highestConstIndex = 0
    val procByName = mutableMapOf<String, Proc>()
    init {
        currentProc = startNewProcedure(0, "main")
    }
    fun nextByte(): EncodedByte {
        require(pos in 0..data.size) { "out of data at pos $pos" }
        val prev     = if (pos > 0) data[pos-1].toInt() and 0xff else null
        val prevCode = currentProc.byteCodes.lastOrNull()
        val value = data[pos++].toInt() and 0xff
        debugByteRead(value)
        val encodedByte: EncodedByte
        try {
            encodedByte = EncodedByte.fromByte(value, prev, prevCode?.byteCode) ?: throw IllegalArgumentException("unable to decode %02x".format(value, prev?:0))
        } catch (e: Exception) {
            throw IllegalArgumentException("error decoding %02x at position %d in %s (prev %02x)".format(value, pos, currentProc.name, prev ?: 0), e)
        }
        debug("$encodedByte ${encodedByte.argument}")

        // resolve constants right away
        if (encodedByte.byteCode == ByteCode.BC_CONST || encodedByte.byteCode == ByteCode.BC_BIGCONST) {
            encodedByte.argument = getConstant(encodedByte.argumentCode)
        }
        currentProc.byteCodes += encodedByte

        // end proc?
        if (encodedByte.byteCode == ByteCode.BC_END) {
            startNewProcedure(0)
        }

        return encodedByte
    }

    fun hasMore(peek: Int = 0) = pos+peek < data.size

    fun getConstant(i: Int): RoseConst {
        require(i in 0 until constants.size) { "Constant not found: %d (max=%d)".format(i, constants.size) }
        if (i > highestConstIndex) highestConstIndex = i
        return constants[i]
    }

    fun startNewProcedure(numArgs: Int, name: String = renamer(procName(procs.size))): Proc {
        val num = procs.size
        val newProc = Proc(num, name, numArgs)
        procs.add(newProc)
        currentProc = newProc
        local.clear()
        for (i in 0 until numArgs) {
            local[i] = LocalVariable(i, argumentName(i))
        }
        procByName[newProc.name] = newProc
        return newProc
    }

    fun debugByteRead(value: Int) {
        if (!DEBUG_DECOMPILE) return
        println("%02x ".format(value))
    }

    fun debug(msg: String) {
        if (!DEBUG_DECOMPILE) return
        println(msg)
    }

    fun writeBytecodes(out: OutputStream) {
        var written = 0
        procs.forEach { proc ->
            out.write(proc.byteCodes.map { it.originalByte.toByte() }.toByteArray())
            written += proc.byteCodes.size
        }
        out.write(END_OF_SCRIPT)
        if (written %2 != 0) out.write(0)
    }

    fun writeConstants(out: OutputStream) {
        constants.forEach { const ->
            writeWord(out, const.i)
            writeWord(out, const.frac)
        }
    }

    fun writeColorscript(out: OutputStream) {
        colorscript.forEach { col ->
            writeWord(out, col.originalWord)
        }
    }

}

fun main() {
    val path = if (USE_LATEST) "Rose/visualizer" else "dumps"
    val colorscriptBin = File("$path/colorscript.bin").readBytes()
    val bytecodes      = File("$path/bytecodes.bin").readBytes()
    val constantsBin   = File("$path/constants.bin").readBytes()
    val constants = readConstants(constantsBin)
    val colorscript = decompileColorscript(colorscriptBin)
    val state = DecompileState(bytecodes, constants, colorscript)
    decompileBytecode(state)
}

fun readConstants(data: ByteArray): List<RoseConst> {
    val ret = mutableListOf<RoseConst>()
    for (i in 0 until data.size/4) {
        val constant = RoseConst(i, readWord(data, i*4), readWord(data, i*4+2))
        ret.add(constant)
        if (DEBUG_CONSTANTS) println("# constant %02d = %s".format(i, constant))
    }
    return ret
}

abstract class ColorScriptEntry(val originalWord: Int, val representation: String)
class ColorScriptWait(word: Int, representation: String) : ColorScriptEntry(word, representation)
class ColorScriptRgb(word: Int, representation: String) : ColorScriptEntry(word, representation)
class ColorScriptEnd(word: Int) : ColorScriptEntry(word, "end")

fun decompileColorscript(data: ByteArray): List<ColorScriptEntry> {
    var pos = 0
    val ret = mutableListOf<ColorScriptEntry>()
    while (pos+1 < data.size) {
        val word = readWord(data, pos)
        // println("# %04x".format(word))
        if (word == COLORSCRIPT_END) {
            ret.add(ColorScriptEnd(word))
            break
        }
        if (word and 0x8000 == 0) {
            val reg = word shr 12
            val rgb = word and 0xfff
            if (DEBUG_COLORSCRIPT) println("\t%d:%03x".format(reg, rgb))
            ret.add(ColorScriptRgb(word, "%d:%03x".format(reg, rgb)))
        } else {
            var wait = 0x10000 - word
            ret.add(ColorScriptWait(word, "wait $wait"))
            // to-rose decompiler specific
            if (pos == 0) wait--
            if (wait > 0) {
                if (DEBUG_COLORSCRIPT) println("\twait %d".format(wait))
            }
        }
        pos += 2
    }
    return ret
}

fun decompileBytecode(state: DecompileState) {
    while (state.hasMore()) {
        state.nextByte()
    }
    state.procs.forEachIndexed { index, proc ->
        proc.resolveProcReferences(state.procs)
    }
}

fun extract(s: String, regex: String): String? {
    return regex.toRegex().matchEntire(s)?.groupValues?.getOrNull(1)
}

fun compileDecompiledLine(line: String, constants: MutableList<RoseConst>): Collection<Any> {
    val elems = line.trim().split("\\s+".toRegex())

    var wantSet = false
    var instruction = elems[0]
    var argument = elems.getOrNull(1)
    if (instruction == "set") {
        wantSet = true
        require(argument != null) { "set command without instruction ($line)" }
        instruction = argument
        argument = elems.getOrNull(2)
    }

    require(elems.isNotEmpty()) { "empty source line" }
    if (instruction.startsWith("BC_")) {
        val name   = extract(instruction, "(BC_\\w+).*")
        val numStr = extract(instruction, "BC_\\w+\\((\\d+)\\)")
        requireNotNull(name) { "expected BC_xxx, but got squat (${instruction})" }
        val byteCode = ByteCode.valueOf(name)
        if (!byteCode.hasArgument()) return listOf(byteCode.code)
        requireNotNull(numStr) { "argument \"(nnn)\" expected, but got ${instruction}" }
        return listOf(byteCode.code + numStr.toInt())
    }
    ByteCode.values().forEach { if (instruction == it.sourceCodeRepresentation) return listOf(it.code) }
    Op.values().forEach { if (instruction == it.symbol) return listOf(ByteCode.BC_OP.code + it.code) }
    if (instruction.startsWith("state.")) {
        val what = extract(instruction, ".*state\\.(\\w+)")
        if (what != null) {
            val code = if (wantSet) ByteCode.BC_WSTATE else ByteCode.BC_RSTATE
            if (what.matches("[A-Z]".toRegex())) {
                val num = what[0].code - 'A'.code
                val stateNum = num + StateVar.ST_WIRE0.ordinal
                val byte = code.code + stateNum
                return listOf(byte)
            }
            StateVar.values().forEach { if (what == it.sourceCodeRepresentation) return listOf(code.code + it.ordinal) }
        }
    }
    if (instruction.startsWith("local.")) {
        val code = if (wantSet) ByteCode.BC_WLOCAL else ByteCode.BC_RLOCAL
        extract(instruction, ".*local\\.([a-z])")?.let { return listOf(code.code + it[0].code - 'a'.code) }
    }
    if (instruction.startsWith("fork(")) {
        extract(instruction, "fork\\((\\d+)\\)")?.let { return listOf(ByteCode.BC_FORK.code + it.toInt()) }
    }
    if (instruction.startsWith("when(")) {
        extract(instruction, "when\\((\\S+)\\)")?.let {
            WhenOp.values().forEach { op -> if (op.symbol == it) return listOf(ByteCode.BC_WHEN.code + op.code) }
        }
    }
    val constValue = fromConstRepresentation(line)
    if (constValue != null) {
        constants.forEach { const ->
            // println("search const ${constValue.toHex()}, is it ${const.toHex()}? -> ${(const.i == constValue.i && const.frac == constValue.frac)}")
            if (const.i == constValue.i && const.frac == constValue.frac) {
                val num = const.index
                if (num >= BIG_CONSTANT_BASE) {
                    return listOf(ByteCode.BC_BIGCONST_MARKER.code, num - BIG_CONSTANT_BASE)
                } else {
                    return listOf(ByteCode.BC_CONST.code + num)
                }
            }
        }
        val num = constants.size
        constants += RoseConst(num, constValue.i, constValue.frac)
        require(constants.size < BIG_CONSTANT_BASE + 255) { "constant pool overflow when adding #$num = ${constants.lastOrNull()}" }
        if (num >= BIG_CONSTANT_BASE) {
            return listOf(ByteCode.BC_BIGCONST_MARKER.code, num - BIG_CONSTANT_BASE)
        } else {
            return listOf(ByteCode.BC_CONST.code + num)
        }
    }
    extract(instruction, "<([^>]+)>")?.let {
        return listOf(ByteCode.BC_PROC.code, ProcNumberOfProcNameReference(it))
    }
    throw IllegalArgumentException("cannot recompile: <$line>")
}

data class ProcNumberOfProcNameReference(val name: String)

fun secondPassDecompile(proc: Proc): List<ValueWithByteRepresentation> {
    val byteCodes = proc.byteCodes
    val src = mutableListOf<EncodedByte>()
    src.addAll(byteCodes)
    val ret = mutableListOf<ValueWithByteRepresentation>()
    var srcPos = 0
    while (srcPos < src.size) {
        val byte = src[srcPos++]
        if (byte.byteCode == ByteCode.BC_CONST) {
            requireNotNull(byte.argument) { "BC_CONST w/o resolved RoseConst" }
            ret += byte.argument as RoseConst
            continue
        }
        if (byte.byteCode == ByteCode.BC_BIGCONST) {
            requireNotNull(byte.argument) { "BC_BIGCONST w/o resolved RoseConst" }
            require(ret.lastOrNull() is EncodedByte
                    && (ret.last() as EncodedByte).byteCode == ByteCode.BC_BIGCONST_MARKER) { "encontered BC_BIGCONST without BC_BIGCONST_MARKER before" }
            ret.removeLast() // pop off BIGCONST_MARKER
            ret += byte.argument as RoseConst
            continue
        }
        if (byte.byteCode == ByteCode.BC_PROC_REF) {
            requireNotNull(byte.argument) { "BC_PROC_REF w/o resolved Proc" }
            require(ret.lastOrNull() is EncodedByte
                    && (ret.last() as EncodedByte).byteCode == ByteCode.BC_PROC) { "encontered BC_PROC_REF without BC_PROC before" }
            ret.removeLast() // pop off PROC
            ret += ProcReference(byte.argument as Proc)
            continue
        }
        ret += byte
    }
    return ret
}

class ProcStack {
    val stack = mutableListOf<Any>()
    fun isEmpty() = stack.isEmpty()
    fun push(value: Any) {
        stack += value
    }
    fun pop() = if (stack.isNotEmpty()) stack.removeLast() else "<emptystack?>"
    fun peek() = stack.lastOrNull()
}

fun annotateStackArguments(instructions: List<ValueWithByteRepresentation>, state: DecompileState) {
    val stack = ProcStack()
    instructions.forEach { instruction ->
        if (instruction is RoseConst || instruction is ProcReference) {
            stack.push(instruction.asSourceCode())
            return@forEach
        }
        if (instruction is EncodedByte) {
            when (instruction.byteCode) {
                ByteCode.BC_FORK -> handleFork(instruction, stack, state)
                ByteCode.BC_WHEN -> handleWhen(instruction, stack)
                ByteCode.BC_OP -> handleOp(instruction, stack)
                ByteCode.BC_NEG -> handleNeg(instruction, stack)
                ByteCode.BC_MUL -> handleMul(instruction, stack)
                ByteCode.BC_DIV -> handleDiv(instruction, stack)
                ByteCode.BC_SINE -> handleSine(instruction, stack)
                ByteCode.BC_POP -> stack.pop()
                ByteCode.BC_WSTATE,
                ByteCode.BC_WLOCAL -> handleWrite(instruction, stack)
                ByteCode.BC_MOVE,
                ByteCode.BC_WAIT -> handleOneArgOp(instruction, stack)
                else -> {
                    if (instruction.hasInput()) instruction.comments += stack.pop().toString()
                    if (instruction.hasOutput()) stack.push(instruction.asSourceCode())
                }
            }
            return@forEach
        }
        throw IllegalArgumentException("unknown instruction object in proc: $instruction")
    }
}

fun handleWhen(byte: EncodedByte, stack: ProcStack) {
    val arg = byte?.argument
    val desc = if (arg is WhenOp) arg.symbol else "<unresolved-when-op?>"
    val value = stack.pop()
    val readable = if (value.toString().contains("<cmp>")) value.toString().replace("<cmp>", desc) else "$value $desc"
    byte.comments += readable
}

fun handleOp(byte: EncodedByte, stack: ProcStack) {
    val arg = byte.argument
    if (arg !is Op) {
        byte.comments += "<unknown-op?>"
    } else {
        var a = stack.pop()
        var b = stack.pop()
        stack.push("($a${arg.symbol}$b)")
    }
}

fun handleFork(byte: EncodedByte, stack: ProcStack, state: DecompileState) {
    val numArgs = byte.argumentCode
    val proc = stack.pop()
    val args = mutableListOf<Any>()
    for (i in 0 until numArgs) {
        args.add(stack.pop())
    }
    val comment = "%s(%s)".format(proc, args.map { "$it" }.reversed().joinToString(", "))
    byte.comments += comment
    extract(proc.toString(), "<([^>]+)>")?.let { name ->
        state.procByName[name]?.let { proc ->
            if (proc.callSignatures.size < 10 && !proc.callSignatures.contains(comment)) proc.callSignatures += comment
        }
    }
}

fun handleWrite(byte: EncodedByte, stack: ProcStack) {
    val arg = stack.pop()
    byte.comments += "${byte.asSourceCode()} $arg"
}

fun handleMul(byte: EncodedByte, stack: ProcStack) {
    var a = stack.pop()
    var b = stack.pop()
    byte.comments += "$a*$b"
    stack.push("$a*$b")
}

fun handleDiv(byte: EncodedByte, stack: ProcStack) {
    var a = stack.pop()
    var b = stack.pop()
    byte.comments += "$a/$b"
    stack.push("$a/$b")
}

fun handleSine(byte: EncodedByte, stack: ProcStack) {
    var a = stack.pop()
    stack.push("sin($a)")
    byte.comments += "sin($a)"

}

fun handleNeg(byte: EncodedByte, stack: ProcStack) {
    val value = stack.pop()
    stack.push("(-$value)")
}

fun handleOneArgOp(byte: EncodedByte, stack: ProcStack) {
    val arg = stack.pop()
    byte.comments += "${byte.asSourceCode()} $arg"
}

fun readWord(data: ByteArray, pos: Int): Int {
    val b1 = data[pos+0].toInt() and 0xff
    val b2 = data[pos+1].toInt() and 0xff
    return (b1 shl 8) or b2
}

fun writeWord(out: OutputStream, i: Int) {
    out.write((i shr 8) and 0xff)
    out.write((i shr 0) and 0xff)
}

fun argumentName(num: Int) = Char('a'.code + num).toString()
fun procName(num: Int) = "proc_%03d".format(num)
fun globalVarName(i: Int) = Char('A'.code + i).toString()
