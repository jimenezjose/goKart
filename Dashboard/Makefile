.SUFFIXES: .java .class
.java.class:
	javac -g $*.java

BUILD_DIR=build
JAR_SOURCES=\
src/utility/lib/jSerialComm-2.5.1.jar
JAVA_SOURCES=\
src/DashboardGUI.java \
src/utility/SerialRoute.java \
src/utility/SerialRouteEvent.java 

default:
	mkdir -p $(BUILD_DIR)
	javac -cp $(JAR_SOURCES) -g $(JAVA_SOURCES) -d $(BUILD_DIR)

clean:
	rm -rf $(BUILD_DIR)
