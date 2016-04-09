package indexbased;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import net.jmatrix.eproperties.EProperties;

import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.TieredMergePolicy;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import utility.Util;

public class IndexMerger {
    private List<FSDirectory> invertedIndexDirectories;
    private List<FSDirectory> forwardIndexDirectories;
    public static String SHARD_STRING = "shards";
    public static File[] nodeDirs;

    public IndexMerger() {
        super();
        this.invertedIndexDirectories = new ArrayList<FSDirectory>();
        this.forwardIndexDirectories = new ArrayList<FSDirectory>();
    }

    private void populateIndexdirs(int shardId) {
        this.invertedIndexDirectories = new ArrayList<FSDirectory>();
        this.forwardIndexDirectories = new ArrayList<FSDirectory>();
        for (File node : IndexMerger.nodeDirs) {
            String invertedIndexDirPath = node.getAbsolutePath()
                    + "/index/shards/" + shardId;
            String forwardIndexDirPath = node.getAbsolutePath()
                    + "/fwdindex/shards/" + shardId;
            File invertedIndexFile = new File(invertedIndexDirPath);
            File forwardIndexFile = new File(forwardIndexDirPath);
            if (invertedIndexFile.exists() && forwardIndexFile.exists()) {
                FSDirectory idir;
                FSDirectory fdir;
                try {
                    idir = FSDirectory.open(invertedIndexFile);
                    fdir = FSDirectory.open(forwardIndexFile);
                    this.invertedIndexDirectories.add(idir);
                    this.forwardIndexDirectories.add(fdir);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                if (!invertedIndexFile.exists()) {
                    System.out
                            .println("inverted index for shard doesn't exist: "
                                    + invertedIndexDirPath);
                }
                if (!forwardIndexFile.exists()) {
                    System.out
                            .println("inverted index for shard doesn't exist: "
                                    + forwardIndexDirPath);
                }
            }
        }
    }

    private void mergeindexes(int shardId) {
        // TODO Auto-generated method stub
        System.out.println("mering inverted indexes");
        WhitespaceAnalyzer whitespaceAnalyzer = new WhitespaceAnalyzer(
                Version.LUCENE_46);
        IndexWriterConfig indexWriterConfig = new IndexWriterConfig(
                Version.LUCENE_46, whitespaceAnalyzer);
        indexWriterConfig.setOpenMode(OpenMode.CREATE);
        TieredMergePolicy mergePolicy = (TieredMergePolicy) indexWriterConfig
                .getMergePolicy();

        mergePolicy.setNoCFSRatio(0);// what was this for?
        mergePolicy.setMaxCFSSegmentSizeMB(0);
        IndexWriter indexWriter = null;
        try {
            FSDirectory dir = FSDirectory.open(new File(Util.INDEX_DIR+"/"+shardId));
            indexWriter = new IndexWriter(dir, indexWriterConfig);
            FSDirectory[] dirs = this.invertedIndexDirectories
                    .toArray(new FSDirectory[this.invertedIndexDirectories
                            .size()]);
            indexWriter.addIndexes(dirs);
            indexWriter.forceMerge(1);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                indexWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println("mergin fwd indexes");
        KeywordAnalyzer keywordAnalyzer = new KeywordAnalyzer();
        IndexWriterConfig fwdIndexWriterConfig = new IndexWriterConfig(
                Version.LUCENE_46, keywordAnalyzer);
        fwdIndexWriterConfig.setOpenMode(OpenMode.CREATE);
        TieredMergePolicy fwdmergePolicy = (TieredMergePolicy) fwdIndexWriterConfig
                .getMergePolicy();

        fwdmergePolicy.setNoCFSRatio(0);// what was this for?
        fwdmergePolicy.setMaxCFSSegmentSizeMB(0);
        indexWriter = null;
        try {

            FSDirectory dir = FSDirectory.open(new File(Util.FWD_INDEX_DIR+"/"+shardId));
            indexWriter = new IndexWriter(dir, fwdIndexWriterConfig);
            FSDirectory[] dirs = this.forwardIndexDirectories
                    .toArray(new FSDirectory[this.forwardIndexDirectories
                            .size()]);
            indexWriter.addIndexes(dirs);
            indexWriter.forceMerge(1);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                indexWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void populateNodeDirs() {
        File currentDir = new File(System.getProperty("user.dir"));
        IndexMerger.nodeDirs = currentDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                // TODO Auto-generated method stub
                return pathname.isDirectory()
                        && pathname.getName().startsWith("NODE");
            }
        });
    }

    public static void main(String[] args) throws FileNotFoundException {
        IndexMerger indexMerger = new IndexMerger();
        EProperties properties = new EProperties();
        FileInputStream fis = null;
        IndexMerger.populateNodeDirs();
        System.out.println("reading Q values from properties file");
        String propertiesPath = System.getProperty("properties.location");
        System.out.println("propertiesPath: " + propertiesPath);
        fis = new FileInputStream(propertiesPath);
        try {
            properties.load(fis);
            String segmentString = properties
                    .getProperty("SHARD_MAX_NUM_TOKENS");
            String[] shardSegments = segmentString.split(",");
            for (int shardId = 1; shardId <= shardSegments.length; shardId++) {
                System.out.println("merging shard "+ shardId);
                indexMerger.populateIndexdirs(shardId);
                indexMerger.mergeindexes(shardId);
            }

        } catch (IOException e) {
            System.out.println("ERROR READING PROPERTIES FILE, "
                    + e.getMessage());
            System.exit(1);
        } finally {

            if (null != fis) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        System.out.println("all merge done!");
    }
}
