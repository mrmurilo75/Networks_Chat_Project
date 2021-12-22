all:
	javac -d ./bin *.java

run:
	cd bin && java ChatClient localhost 8000 && cd ..

