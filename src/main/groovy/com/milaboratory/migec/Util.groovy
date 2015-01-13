package com.milaboratory.migec

import java.util.concurrent.atomic.AtomicIntegerArray
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 Copyright 2014 Mikhail Shugay (mikhail.shugay@gmail.com)

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
class Util {
    /*
     * Execution utils
     */

    static Object run(Script script, String args) {
        // perform cleanup
        def argArray = args.split(" ").
                findAll { it != " " && it != "" }.
                collect { it.replaceAll("//+", "/").toString() }
        println "Executing ${script.class.canonicalName} ${argArray.join(" ")}"
        script.binding.setVariable("args", argArray)
        script.run()
    }
    
    static final String BLANK_PATH = "."

    static final List<String> FILE_TYPES = ["paired", "unpaired", "overlapped"],
                              MASKS = ["0:1", "1:0", "1:1", "0:0"]

    static final String ASSEMBLE_LOG_HEADER =
            "#SAMPLE_ID\tSAMPLE_TYPE\tINPUT_FASTQ1\tINPUT_FASTQ2\tOUTPUT_ASSEMBLY1\tOUTPUT_ASSEMBLY2\t" +
                    "MIG_COUNT_THRESHOLD\t" +
                    "MIGS_GOOD_FASTQ1\tMIGS_GOOD_FASTQ2\tMIGS_GOOD_TOTAL\tMIGS_TOTAL\t" +
                    "READS_GOOD_FASTQ1\tREADS_GOOD_FASTQ2\tREADS_GOOD_TOTAL\tREADS_TOTAL\tREADS_DROPPED_WITHIN_MIG"

    static final String CDRBLAST_LOG_HEADER =
            "#SAMPLE_ID\tDATA_TYPE\tOUTPUT_FILE\tINPUT_FILES\t" +
                    "EVENTS_GOOD\tEVENTS_MAPPED\tEVENTS_TOTAL\t" +
                    "READS_GOOD\tREADS_MAPPED\tREADS_TOTAL"

    static final String CDRBLASTFILTER_LOG_HEADER =
            "#SAMPLE_ID\tOUTPUT_FILE\tINPUT_RAW\tINPUT_ASM\t" +
                    "CLONOTYPES_FILTERED\tCLONOTYPES_TOTAL\t" +
                    "EVENTS_FILTERED\tEVENTS_TOTAL\t" +
                    "READS_FILTERED\tREADS_TOTAL\t" +
                    "NON_FUNCTIONAL_CLONOTYPES\tNON_FUNCTIONAL_EVENTS\tNON_FUNCTIONAL_READS"

    /*
     * Immune gene segment utils
     */

    static void listAvailableSegments(boolean includeNonFunctional) {
        println "SPECIES\tGENE"
        new InputStreamReader(Migec.class.classLoader.getResourceAsStream("segments" +
                (includeNonFunctional ? "_all" : "") + ".metadata")).splitEachLine("\t") { splitLine ->
            if (!splitLine[0].startsWith("#") && splitLine[-1] == "1") {
                println "${splitLine[0]}\t${splitLine[1]}"
            }
        }
    }

    static boolean isAvailable(String species, String gene, boolean includeNonFunctional) {
        boolean result = false
        new InputStreamReader(Migec.class.classLoader.getResourceAsStream("segments" +
                (includeNonFunctional ? "_all" : "") + ".metadata")).splitEachLine("\t") { splitLine ->
            if (!splitLine[0].startsWith("#") && splitLine[-1] == "1" &&
                    splitLine[0].toUpperCase() == species.toUpperCase() &&
                    splitLine[1].toUpperCase() == gene.toUpperCase())
                result = true
        }
        result
    }

    static InputStreamReader getSegmentsFile(boolean includeNonFunctional) {
        new InputStreamReader(Migec.class.classLoader.getResourceAsStream("segments" +
                (includeNonFunctional ? "_all" : "") + ".txt"))
    }

    /*
     * Phred utils
     */

    static byte qualFromSymbol(char symbol) {
        (int) symbol - 33
    }

    static byte minQual(String qual) {
        qualFromString(qual).collect().min()
    }

    static byte[] qualFromString(String qual) {
        byte[] qualArr = new byte[qual.length()]
        for (int i = 0; i < qual.length(); i++)
            qualArr[i] = qualFromSymbol(qual.charAt(i))
        qualArr
    }

    static char symbolFromQual(int qual) {
        qual = qual < 2 ? 2 : qual
        qual = qual > 40 ? 40 : qual
        (char) (qual + 33)
    }

    /*
     * NT utils
     */

    static final char[] NTS = ['A', 'T', 'G', 'C']

    static char code2nt(int code) {
        switch (code) {
            case 0:
                return 'A'
            case 1:
                return 'T'
            case 2:
                return 'G'
            case 3:
                return 'C'
        }
    }

    static byte nt2code(char symbol) {
        switch (symbol) {
            case 'A':
                return 0
            case 'T':
                return 1
            case 'G':
                return 2
            case 'C':
                return 3
        }
    }

    static String revCompl(String seq) {
        def chars = seq.reverse().toCharArray()
        for (int i = 0; i < chars.length; i++) {
            switch (chars[i]) {
                case ((char) 'A'):
                    chars[i] = (char) 'T'
                    break
                case ((char) 'T'):
                    chars[i] = (char) 'A'
                    break
                case ((char) 'G'):
                    chars[i] = (char) 'C'
                    break
                case ((char) 'C'):
                    chars[i] = (char) 'G'
                    break
                default:
                    chars[i] = (char) 'N'
                    break
            }
        }
        return new String(chars)
    }

    static String revComplExt(String seq) {
        def chars = seq.reverse().toCharArray()
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == (char) 'A')
                chars[i] = (char) 'T'
            else if (chars[i] == (char) 'T')
                chars[i] = (char) 'A'
            else if (chars[i] == (char) 'G')
                chars[i] = (char) 'C'
            else if (chars[i] == (char) 'C')
                chars[i] = (char) 'G'
            else if (chars[i] == (char) 'N')
                chars[i] = (char) 'N'
            else if (chars[i] == (char) 'a')
                chars[i] = (char) 't'
            else if (chars[i] == (char) 't')
                chars[i] = (char) 'a'
            else if (chars[i] == (char) 'g')
                chars[i] = (char) 'c'
            else if (chars[i] == (char) 'c')
                chars[i] = (char) 'g'
            else
                chars[i] = (char) 'N'
        }
        return new String(chars)
    }

    /*
     * Amino acid utils
     */

    static String codon2aa(String codon) {
        switch (codon.toUpperCase()) {
            case 'TTT': case 'TTC': return 'F'
            case 'TTA': case 'TTG': return 'L'
            case 'TCT': case 'TCC': case 'TCA': case 'TCG': return 'S'
            case 'TAT': case 'TAC': return 'Y'
            case 'TAA': case 'TAG': case 'TGA': return '*'
            case 'TGT': case 'TGC': return 'C'
            case 'TGG': return 'W'
            case 'CTT': case 'CTC': case 'CTA': case 'CTG': return 'L'
            case 'CCT': case 'CCC': case 'CCA': case 'CCG': return 'P'
            case 'CAT': case 'CAC': return 'H'
            case 'CAA': case 'CAG': return 'Q'
            case 'CGT': case 'CGC': case 'CGA': case 'CGG': return 'R'
            case 'ATT': case 'ATC': case 'ATA': return 'I'
            case 'ATG': return 'M'
            case 'ACT': case 'ACC': case 'ACA': case 'ACG': return 'T'
            case 'AAT': case 'AAC': return 'N'
            case 'AAA': case 'AAG': return 'K'
            case 'AGT': case 'AGC': return 'S'
            case 'AGA': case 'AGG': return 'R'
            case 'GTT': case 'GTC': case 'GTA': case 'GTG': return 'V'
            case 'GCT': case 'GCC': case 'GCA': case 'GCG': return 'A'
            case 'GAT': case 'GAC': return 'D'
            case 'GAA': case 'GAG': return 'E'
            case 'GGT': case 'GGC': case 'GGA': case 'GGG': return 'G'
            default: return '?'
        }
    }

    static String translate(String seq) {
        def aaSeq = ""
        def oof = seq.size() % 3
        if (oof > 0) {
            def mid = (int) (seq.size() / 2)
            seq = seq.substring(0, mid) + ("?" * (3 - oof)) + seq.substring(mid, seq.length())
        }

        def leftEnd = -1, rightEnd = -1
        for (int i = 0; i <= seq.size() - 3; i += 3) {
            def codon = seq.substring(i, i + 3)
            if (codon.contains("?")) {
                leftEnd = i
                break
            }
            aaSeq += codon2aa(codon)
        }

        if (oof == 0)
            return aaSeq

        def aaRight = ""
        for (int i = seq.size(); i >= 3; i -= 3) {
            def codon = seq.substring(i - 3, i)
            if (codon.contains("?")) {
                rightEnd = i
                break
            }
            aaRight += codon2aa(codon)
        }

        return aaSeq + seq.substring(leftEnd, rightEnd).toLowerCase() + aaRight.reverse()
    }

    /*
     * I/O utils
     */

    static BufferedReader getReader(String fname) {
        new BufferedReader(new InputStreamReader(fname.endsWith(".gz") ? new GZIPInputStream(new FileInputStream(fname)) :
                new FileInputStream(fname)))
    }

    static BufferedWriter getWriter(String outfile, boolean compressed) {
        getWriter(outfile, compressed, false)
    }

    static BufferedWriter getWriter(String outfile, boolean compressed, boolean append) {
        if (compressed)
            outfile += ".gz"

        def fos = new FileOutputStream(outfile, append)

        new BufferedWriter(new OutputStreamWriter(compressed ?
                new GZIPOutputStream(fos) : fos))
    }

    static BufferedWriter getWriter(String outfile) {
        boolean compressed = outfile.endsWith(".gz")
        new BufferedWriter(new OutputStreamWriter(compressed ?
                new GZIPOutputStream(new FileOutputStream(outfile)) : new FileOutputStream(outfile)))
    }

    static String getFileName(String fullFileName) {
        fullFileName.split("/")[-1]
    }

    static String getFastqPrefix(String fileName) {
        fileName.split("/")[-1].replaceAll(/\.fastq(?:\.gz)?$/, "")
    }

    /*
     * UMI utils
     */

    static final byte DEFAULT_UMI_QUAL_THRESHOLD = (byte) 15

    static String getUmi(String header, byte umiQualThreshold) {
        def splitHeader = header.split(" ")
        def umiEntry = splitHeader.find { it.startsWith("UMI:") }
        if (umiEntry == null) {
            println "[ERROR] no UMI header in input. Terminating"
            System.exit(-1)
        }
        String umi = umiEntry.split(":")[1] // quality can contain :
        for (int i = umiEntry.length() - umi.length(); i < umiEntry.length(); i++)
            if (qualFromSymbol(umiEntry.charAt(i)) < umiQualThreshold)
                return null
        umi
    }

    /*
     * Misc utils
     */

    static String toString(AtomicIntegerArray arr) {
        (0..<arr.length()).collect { arr.get(it) }.join("\t")
    }

    static String getPercent(int n, int N) {
        "${((int) (10000 * (double) n / (double) N)) / 100}%"
    }
}
