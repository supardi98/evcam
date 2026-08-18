import java.io.File
var braces = 0
val lines = File("app/src/main/kotlin/net/supardi/evcam/ui/TopCameraBar.kt").readLines()
for ((i, line) in lines.withIndex()) {
    braces += line.count { it == '{' }
    braces -= line.count { it == '}' }
    if (braces < 0) { println("Extra closing brace at line ${i+1}: $line"); braces = 0 }
}
println("Final braces count: $braces")
