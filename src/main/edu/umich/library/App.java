package edu.umich.library;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.marc4j.*;
import org.marc4j.marc.*;
import org.marc4j.marc.Record;
import picocli.CommandLine;
import picocli.CommandLine.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class App implements Runnable {

    @Option(names = {"-s", "--stdout"}, description = "Write to STDOUT")
    Boolean stdout = null;

    @Parameters(arity = "1..*", paramLabel = "FILE", description = "File(s) to process.")
    List<String> filenames = new ArrayList<>();

    List<String> outfilenames = new ArrayList<>();

    private InputStream singleFileInputStreamFromTarGz(String tarFile) throws IOException {
        InputStream in = new GzipCompressorInputStream(new FileInputStream(tarFile));
        TarArchiveInputStream tar = new TarArchiveInputStream(in);
        tar.getNextTarEntry();
        return new BufferedInputStream(tar);
    }

    private List<MarcReader> xmlReaders() {
        List<MarcReader> readers = new ArrayList<>();

        try {
            if (filenames.isEmpty()) {
                readers.add(new MarcXmlReader(new BufferedInputStream(System.in)));
                filenames.add("STDIN");
            }
            for (String filename : filenames) {
                readers.add(new MarcXmlReader(singleFileInputStreamFromTarGz(filename)));
                outfilenames.add(getOutputFileName(filename));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return readers;
    }

//  private MarcMultiplexReader inputReader() throws IOException {
//    List<MarcReader> readers = new ArrayList<>();
//
//    if (filenames.isEmpty()) {
//      readers.add(new MarcXmlReader(new BufferedInputStream(System.in)));
//      outfilenames.add("STDOUT");
//    } else {
//      for (String filename : filenames) {
//        readers.add(new MarcXmlReader(singleFileInputStreamFromTarGz(filename)));
//        outfilenames.add(getOutputFileName(filename));
//      }
//    }
//    return new MarcMultiplexReader(readers, filenames);
//  }

//  private MarcJsonWriter outputWriter() {
//    OutputStream out = new BufferedOutputStream(System.out);
//    if (outfile != null) {
//      try {
//        FileOutputStream fileOutputStream = new FileOutputStream(outfile);
//        out = new BufferedOutputStream(fileOutputStream);
//      } catch (FileNotFoundException e) {
//        System.err.println(e.getMessage());
//      }
//    }
//    MarcJsonWriter writer =  new MarcJsonWriter(out);
//    writer.setUnicodeNormalization(true);
//    return writer;
//  }


    private OutputStreamWriter outputWriter(String outfile) throws RuntimeException {
        OutputStreamWriter osw = null;
        if (outfile.equals("STDOUT")) {
            osw = new OutputStreamWriter(System.out, StandardCharsets.UTF_8);
        } else {
            try {
                var fos = new FileOutputStream(outfile);
                var zw = new GzipCompressorOutputStream(fos);
                osw = new OutputStreamWriter(zw, StandardCharsets.UTF_8);
            } catch (FileNotFoundException e) {
                System.err.println(e.getMessage());
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage());
            }
        }
        return osw;
    }

    private Map<String, Object> dataFieldJsonHash(DataField df) {
        ArrayList<Map<String, String>> subfields = new ArrayList<Map<String, String>>();
        for (Subfield sf : df.getSubfields()) {
            subfields.add(Map.of(String.valueOf(sf.getCode()), sf.getData()));
        }
        var field = new HashMap<String, Object>();
        field.put("ind1", df.getIndicator1());
        field.put("ind2", df.getIndicator2());
        field.put("subfields", subfields);
        return Map.of(df.getTag(), field);
    }

    private Map<String, Object> marcRecordAsJsonHash(Record r) {
        Map<String, Object> rv = new HashMap<>();
        rv.put("leader", r.getLeader().toString());
        List<Map<String, Object>> fields = new ArrayList<>();
        for (VariableField f : r.getVariableFields()) {
            if (f instanceof ControlField) {
                ControlField cf = (ControlField) f;
                fields.add(Map.of(cf.getTag(), cf.getData()));
            } else if (f instanceof DataField) {
                var df = (DataField) f;
                fields.add(dataFieldJsonHash((DataField) df));
            }
        }
        rv.put("fields", fields);
        return rv;
    }

    public String getOutputFileName(String s) {
        if (s.equals("STDIN")) {
            return "STDOUT";
        }
        String p = new File(s).getName();
        Boolean x = p.endsWith(".tar.gz");
        if (p.endsWith(".tar.gz")) {
            p = p.replace(".tar.gz", ".jsonl.gz");
        } else {
            System.err.println("System only words with stupid Alma .tar.gz files right now, not " + s);
        }
        return p;
    }

    public void run() {
        ObjectMapper objectMapper = new ObjectMapper();
        var readers = xmlReaders();
        var nameIter = outfilenames.iterator();
        for (MarcReader reader : readers) {
            String currentFileName = nameIter.next();
            var outputWriter = outputWriter(currentFileName);
            System.err.println("Writing to " + currentFileName);
            while (reader.hasNext()) {
                Record r = reader.next();
                try {
                    Map jhash = marcRecordAsJsonHash(r);
                    String jj = objectMapper.writeValueAsString(jhash);
                    outputWriter.write(jj);
                    outputWriter.write("\n");
                } catch (MarcException e) {
                    System.err.println(r.getControlNumber() + " " + e.getMessage());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            try {
                outputWriter.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new App()).execute(args);
        System.exit(exitCode);
    }
}

