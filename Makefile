# Get names of *.java files by looking through src/
JAVA1=$(addsuffix .java, $(subst src/,java/,$(basename $(wildcard src/*.nw))))
JAVA2=$(addsuffix .java, $(subst src/desktop/,java/desktop/,$(basename $(wildcard src/desktop/*.nw))))

VERSION=1.0.0

.PHONY : _mod all dep src jar pdf purge clean

# Does the user have the required build tools?
bin_make:=$(shell command -v make 2> /dev/null)
bin_wget:=$(shell command -v wget 2> /dev/null)
bin_latexmk:=$(shell command -v latexmk 2> /dev/null)
bin_pdflatex:=$(shell command -v pdflatex 2> /dev/null)
bin_javac:=$(shell command -v javac 2> /dev/null)
bin_jar:=$(shell command -v jar 2> /dev/null)
bin_texfot:=$(shell command -v texfot 2> /dev/null)
bin_unzip:=$(shell command -v unzip 2> /dev/null)
bin_noweave:=$(shell command -v noweave 2> /dev/null)
bin_notangle:=$(shell command -v notangle 2> /dev/null)

_mod :
	@printf "_______________________________________________________________\n"
	@printf "Jargo Build System\n"
	@printf "  Jargo Version: $(VERSION) (January 20, 2020)\n"
	@printf "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
	@printf "Required build tools:\n"
ifndef bin_make
	$(error "**ERROR: 'make' not found!")
else ifndef bin_wget
	$(error "**ERROR: 'wget' not found!")
else ifndef bin_latexmk
	$(error "**ERROR: 'latexmk' not found!")
else ifndef bin_pdflatex
	$(error "**ERROR: 'pdflatex' not found!")
else ifndef bin_javac
	$(error "**ERROR: 'javac' not found!")
else ifndef bin_jar
	$(error "**ERROR: 'jar' not found!")
else ifndef bin_texfot
	$(error "**ERROR: 'texfot' not found!")
else ifndef bin_unzip
	$(error "**ERROR: 'unzip' not found!")
endif
ifndef bin_noweave
	$(warning "**WARNING: 'noweave' not found! target 'src' unavailable.")
endif
ifndef bin_notangle
	$(warning "**WARNING: 'notangle' not found! target 'src' unavailable.")
endif
	@printf "  found make: $(bin_make)\n"
	@printf "  found wget: $(bin_wget)\n"
	@printf "  found latexmk: $(bin_latexmk)\n"
	@printf "  found pdflatex: $(bin_pdflatex)\n"
	@printf "  found javac: $(bin_javac)\n"
	@printf "  found jar: $(bin_jar)\n"
	@printf "  found texfot: $(bin_texfot)\n"
	@printf "  found unzip: $(bin_unzip)\n"
	@printf "  found noweave: $(bin_noweave)\n"
	@printf "  found notangle: $(bin_notangle)\n"
	@printf "\n"
	@printf "Commands:\n"
	@printf "  make all        build library, documentation, and fetch deps\n"
	@printf "  make jar        build library only (jar/jargors-$(VERSION).jar)\n"
	@printf "  make pdf        build documentation only (pdf/jargo.pdf)\n"
	@printf "  make src        build Java sources from Noweb files\n"
	@printf "  make dep        fetch dependencies from the Internet (dep/)\n"
	@printf "  make clean      delete jar/, com/, pdf/, build.log, wget.log\n"
	@printf "  make purge      clean + delete Java sources, doc/body.tex\n"
	@printf "  make purgedep   delete dependencies (dep/)\n"
	@printf "\n"
	@printf "If you experience any problems, please log an issue at\n"
	@printf "  https://github.com/jargors/Jargo/issues\n"
	@printf "\n"
	@printf "Or to be a contributor, you can fork this repository,\n"
	@printf "make changes in your fork, and submit a pull request\n"
	@printf "  https://github.com/jargors/Jargo/pulls\n"
	@printf "\n"
	@printf "Thank you!\n"
	@printf "===============================================================\n"

all : dep jar pdf
	@printf "done all\n"

dep :
	@printf "make dep\n"
	@make -s -f makedep.mk
	@printf "done dep\n"

#jar : src
jar :
	@printf "make jar\n"
	@make -s -f makejar.mk
	@printf "done jar\n"

#pdf : src
pdf :
	@printf "make pdf\n"
	@make -s -f makepdf.mk
	@printf "done pdf\n"

#src : $(JAVA1) $(JAVA2)
src :
	@printf "make src\n"
	@make -s -f makesrc.mk
	@printf "done src\n"

clean :
	@printf "make clean\n"
	@if [ -d "jar" ]; then printf "remove jar/...\n"; rm -rf jar/ ; fi; \
	 if [ -d "com" ]; then printf "remove com/...\n"; rm -rf com/ ; fi; \
	 if [ -d "pdf" ]; then printf "remove pdf/...\n"; rm -rf pdf/ ; fi; \
	 if [ -s "build.log" ]; then printf "remove build.log...\n"; rm -rf build.log ; fi; \
	 if [ -s "wget.log" ]; then printf "remove wget.log...\n"; rm -rf wget.log ; fi;
	@printf "done clean\n"

purge : clean
	@printf "make purge\n"
	@count=`ls -1 java/*.java 2>/dev/null | wc -l`; \
	 if [ $$count != 0 ]; then printf "purge java/ srcs...\n"; rm -f $(JAVA1) $(JAVA2) ; fi; \
	 if [ -f "doc/body.tex" ]; then printf "purge doc/body.tex...\n"; rm -f doc/body.tex ; fi;
	@printf "done purge\n"

purgedep :
	@printf "make purgedep\n"
	@if [ -d "dep" ]; then printf "purge dep/...\n"; rm -rf dep/ ; fi;
	@printf "done purge dep\n"

