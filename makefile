all:
	javac -d ./bin *.java

run:
	cd bin && java Main.class && cd ..

