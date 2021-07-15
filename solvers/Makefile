SOURCEPATH = .
CLASSPATH = .:../jar/*:../dep/*
JFLAGS = -g -classpath $(CLASSPATH) -sourcepath $(SOURCEPATH)
JC = javac
.SUFFIXES: .java .class
.java.class:
		$(JC) $(JFLAGS) $*.java -Xlint:unchecked

CLASSES = \
		ridesharing/Key.java \
        ridesharing/HungarianAlgorithm.java \
		ridesharing/ContextMappingModule.java \
		ridesharing/DemandPredictionModule.java \
		ridesharing/DNNModel.java \
		ridesharing/FrequentistModel.java \
		ridesharing/PathComputationModule.java \
        ridesharing/CostComputationModule.java \
		ridesharing/RequestCollectionModule.java \
		ridesharing/OptimizationModule.java \
        ridesharing/RideSharingAlgorithm.java

.PHONY : all compile jar clean

all : compile jar

compile: $(CLASSES:.java=.class)

jar: compile
	@mkdir -p jar/
	@jar cvf jar/solvers.jar ridesharing/Key.class \
								ridesharing/HungarianAlgorithm.class \
								ridesharing/OptimizationModule.class \
								ridesharing/ContextMappingModule.class \
								ridesharing/DemandPredictionModule.class \
								ridesharing/DNNModel.class \
								ridesharing/FrequentistModel.class \
								ridesharing/CostComputationModule.class \
								ridesharing/PathComputationModule.class \
								ridesharing/RequestCollectionModule.class \
								ridesharing/RideSharingAlgorithm.class

clean:
	$(RM) ridesharing/*.class jar/*.jar
