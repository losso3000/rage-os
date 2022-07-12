import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter

fun main() {
    val from = File("dumps/logicos-script.txt")
    val to   = File("demo/horse.txt")
    var done = false
    while (!done) {
        val procs1 = getProcs(from)
        val procs2 = getProcs(to)
        println("$from: ${procs1.size}")
        println("$to: ${procs2.size}")
        val refs2 = getRefs(procs2.values)
        println(procs2.keys.sorted())
        println(refs2)
        done = true
        PrintWriter(
            OutputStreamWriter(FileOutputStream(to, true))).use { w ->
            refs2.filter { name -> println("$name in ${procs2.keys.sorted()}?"); !procs2.contains(name) }.map { name ->
                val proc = procs1[name]
                if (proc == null) {
                    println("warn: missing proc in $from: $name")
                } else {
                    println("not in $to: $name")
                    // println("----> $proc <----")
                    w.println(proc)
                    done = false
                }
            }
        }
    }
}

fun getRefs(values: Collection<String>): List<String> {
    val ret = mutableListOf<String>()
    values.map { s ->
        val refs = mutableListOf<String>()
        "<([^>\\s]+)>".toRegex().findAll(s).map { m -> m.groupValues[1] }.toCollection(refs)
        println("refs in <<<$s>>> = $refs")
        refs
    }.forEach { procRefs ->
        procRefs.forEach { name ->
            if (!ret.contains(name)) ret += name
        }
    }
    return ret
}


fun getProcs(file: File): Map<String, String> {
    var procName: String? = null
    var proc = ""
    var ret = mutableMapOf<String, String>()
    file.readLines().forEach { line ->
        if (procName == null) {
            extract(line, "proc (\\S+).*")?.let { name ->
                procName = name
                proc = "$line\n"
            }
        } else {
            proc += "$line\n"
            if (line.trim() == "end") {
                ret[procName!!] = proc
                procName = null
            }
        }
        // if (file.name.contains("horse")) println("$line... procName=$procName proc ${proc.length}")
    }
    return ret
}
