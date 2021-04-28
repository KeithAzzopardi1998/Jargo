JFLAGS = -g -cp .:../jar/*
JC = javac
.SUFFIXES: .java .class
.java.class:
		$(JC) $(JFLAGS) $*.java -Xlint:unchecked

CLASSES = \
        baseline/HungarianAlgorithm.java \
        baseline/CostComputationModule.java \
        baseline/RideSharingAlgorithm.java

.PHONY : all compile jar clean

all : compile jar

compile: $(CLASSES:.java=.class)

jar: compile
	@mkdir -p jar/
	@jar cvf jar/baseline.jar baseline/HungarianAlgorithm.class baseline/RideSharingAlgorithm.class baseline/CostComputationModule.class

clean:
	$(RM) baseline/*.class jar/*.jar
