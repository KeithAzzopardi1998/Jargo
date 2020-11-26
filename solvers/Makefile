JFLAGS = -g -cp .:../jar/*
JC = javac
.SUFFIXES: .java .class
.java.class:
		$(JC) $(JFLAGS) $*.java

CLASSES = \
        HungarianAlgorithm.java \
        MLridesharing.java \
        Exhaustive.java

default: classes jar 

classes: $(CLASSES:.java=.class)

jar: 
	@jar cvf solvers.jar HungarianAlgorithm.class MLridesharing.class Exhaustive.class

clean:
		$(RM) *.class solvers.jar
