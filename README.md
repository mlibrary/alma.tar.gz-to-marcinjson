# alma.tar.gz-to-marcinjson
Turn alma marc-xml export files into nicer marc-in-json jsonl files

Alma exports marc-xml files as a bunch of `<whatever>.tar.gz` files, each of which has the _single file_ `<whatever>.xml` in it.

This is code that creates a fat .jar (i.e., all dependencies included) that will take any number of `<whatever>.tar.gz` files and produce
`<whatever>.jsonl.gz` files in the directory you invoked the program from. 

## Usage

This is an executable .jar file that only takes filenames to convert as arguments. 

```
java -jar /path/to/alma.tar.gz-to-marcinxml /path/to/alma/*.tar.gz

```
