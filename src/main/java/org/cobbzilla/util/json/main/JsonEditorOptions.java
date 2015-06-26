package org.cobbzilla.util.json.main;

import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.util.json.JsonEditOperationType;
import org.kohsuke.args4j.Option;

import java.io.File;

public class JsonEditorOptions {

    public static final String USAGE_CONFIG_FILE = "The JSON file to source. Default is standard input.";
    public static final String OPT_CONFIG_FILE = "-f";
    public static final String LONGOPT_CONFIG_FILE = "--file";
    @Option(name=OPT_CONFIG_FILE, aliases=LONGOPT_CONFIG_FILE, usage=USAGE_CONFIG_FILE)
    @Getter @Setter private File jsonFile;

    public boolean hasJsonFile () { return jsonFile != null; }

    public static final String USAGE_OPERATION = "The operation to perform.";
    public static final String OPT_OPERATION = "-o";
    public static final String LONGOPT_OPERATION = "--operation";
    @Option(name=OPT_OPERATION, aliases=LONGOPT_OPERATION, usage=USAGE_OPERATION)
    @Getter @Setter private JsonEditOperationType operationType = JsonEditOperationType.read;

    public static final String USAGE_PATH = "The path to the JSON node where the append or replace will take place. " +
            "Default is root node for append operations. For replace, you must specify a path.";
    public static final String OPT_PATH = "-p";
    public static final String LONGOPT_PATH = "--path";
    @Option(name=OPT_PATH, aliases=LONGOPT_PATH, usage=USAGE_PATH)
    @Getter @Setter private String path;

    public static final String USAGE_VALUE = "The JSON data to append or update. Required for write operations.";
    public static final String OPT_VALUE = "-v";
    public static final String LONGOPT_VALUE = "--value";
    @Option(name=OPT_VALUE, aliases=LONGOPT_VALUE, usage=USAGE_VALUE)
    @Getter @Setter private String value;

    public static final String USAGE_OUTPUT = "The output file. Default is standard output.";
    public static final String OPT_OUTPUT = "-w";
    public static final String LONGOPT_OUTPUT = "--outfile";
    @Option(name=OPT_OUTPUT, aliases=LONGOPT_OUTPUT, usage=USAGE_OUTPUT)
    @Getter @Setter private File outfile;

    public boolean hasOutfile () { return outfile != null; }

}
