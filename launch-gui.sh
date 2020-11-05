#!/usr/bin/env bash
#_CLASSPATH=.:jar/*:dep:dep/*:example:example/jar/*
_CLASSPATH=.:jar/*:dep:dep/*:solvers:solvers/jar/*
java \
    --module-path dep \
    --add-modules javafx.controls,javafx.fxml,javafx.swing \
    -Xmx6g \
    -Djava.library.path=dep \
    -Dderby.storage.pageCacheSize=8000 \
    -Djargors.controller.debug=false \
    -Djargors.desktop.debug=false \
    -cp $_CLASSPATH:$DERBY_HOME/lib/derby.jar \
com.github.jargors.ui.Desktop

