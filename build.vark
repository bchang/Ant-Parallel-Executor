var srcDir = file("src")
var buildDir = file("build")
var classesDir = buildDir.file("classes")
var distDir = buildDir.file("dist")

function setup() {
  Ant.mkdir(:dir = buildDir)
}

@Depends("setup")
function compile() {
  Ant.mkdir(:dir = classesDir)
  Ant.javac(:srcdir = path(srcDir),
            :destdir = classesDir,
            :classpath = path(file("lib").fileset("*.jar")),
            :includeantruntime = false)
}

@Depends("compile")
function jar() {
  Ant.mkdir(:dir = distDir)
  Ant.jar(:destfile = distDir.file("sampleproject.jar"),
          :basedir = classesDir)
}

function clean() {
  Ant.delete(:dir = buildDir)
}
