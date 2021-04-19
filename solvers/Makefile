JFLAGS = -g -cp .:../jar/*
JC = javac
.SUFFIXES: .java .class
.java.class:
		$(JC) $(JFLAGS) $*.java -Xlint:unchecked

CLASSES = \
        HungarianAlgorithm.java \
        MLridesharing.java \
        Baseline.java

default: classes jar 

classes: $(CLASSES:.java=.class)

jar:
	@mkdir -p jar/
	@jar cvf jar/solvers.jar HungarianAlgorithm.class MLridesharing.class Baseline.class

clean:
		$(RM) *.class jar/solvers.jar
