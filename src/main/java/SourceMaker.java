import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static java.nio.file.FileVisitResult.*;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class SourceMaker {
    
    final static String SOURCES_JAR = "-sources.jar";
    
    Map<String, String> env = new HashMap<String, String>(); 
    
    public static void main(String[] args) throws IOException {
        
        if (args.length != 3) {
            System.err.println("need 3 parameters: \n"
                    + " 1) location where search source;\n"
                    + " 2) location where search jars;\n"
                    + " 3) pattern at jar path;");
            System.exit(1);
        }
        
        Path startingDir = Paths.get(args[0]);
        
        SourceMaker maker = new SourceMaker(startingDir);
        
        maker.search(Paths.get(args[1]), args[2]);
    }
    
    Path startingDir;

    public SourceMaker(Path startingDir) throws IOException {
        env.put("create", "true");
        
        this.startingDir = startingDir;
    }
    
    private void search(Path searchPath, String toHave) throws IOException {
        SearchJar sj = new SearchJar(toHave);
        
        Files.walkFileTree(searchPath, sj);
    }

    public Path process(Path startingDir, Path jarFile) throws IOException {

        Path location = null;
        
        String sourceName = jarFile.getFileName().toString().replace(".jar", "-sources.jar");
        URI sourceUri = URI.create("jar:file:"+jarFile.getParent()+"/"+sourceName);
        
        System.out.println(sourceUri);
        
        try (FileSystem zipfs = FileSystems.newFileSystem(sourceUri, env)) {
            
            try (JarFile jar = new JarFile(jarFile.toFile())) {
                
                Enumeration<JarEntry> entries = jar.entries();
                
                while (entries.hasMoreElements()) {
                    
                    JarEntry file = entries.nextElement();
                    
                    String name = file.getName();
                    
                    if (name.startsWith("META-INF")) continue;
                    if (name.endsWith("/")) continue;
                    if (name.contains("$")) continue;
                    
                    name = name.replace(".class", ".java");
                    
                    if (location != null) {
                        Path srcPath = location.resolve(name);
                        
                        if (Files.exists(srcPath)) {

                            Path pathInZip = zipfs.getPath(name);
                            
                            store(zipfs, pathInZip, srcPath);
                            
                        } else {
                            location = null;
                        }
                    }
                    
                    if (location == null) {
                        //search
                        
                        PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:**/"+name);
                        
                        SearchSourceFiles pf = new SearchSourceFiles(pathMatcher);
                        
                        Files.walkFileTree(startingDir, pf);
                        
                        if (pf.location == null) {
                            System.out.println("not found "+name);
                            continue;
                        }
                        
                        Path pathInZip = zipfs.getPath(name);
                        
                        store(zipfs, pathInZip, pf.location);
                        
                        location = pf.location;
                        for (int i = 0; i < pathInZip.getNameCount(); i++) {
                            location = location.getParent();
                        }
                    }
                }
            }
        }
        
        return jarFile.getParent().resolve(sourceName);
    }
    
    private void store(FileSystem zipfs, Path pathInZip, Path file) throws IOException {
        Files.createDirectories(pathInZip.getParent());

        Files.copy(file, pathInZip, StandardCopyOption.REPLACE_EXISTING);
    }
    
    class SearchJar extends SimpleFileVisitor<Path> {
        
        String contains;

        public SearchJar(String contains) {
            this.contains = contains;
        }
        
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attr) {
            
            if (attr.isRegularFile()) {
                String path = file.toString();

                if (path.endsWith(".jar") && path.contains(contains) && !path.contains("-sources")) {
                    try {
                        process(startingDir, file);
                    } catch (IOException e) {
                        System.err.println("ERROR on "+file);
                        e.printStackTrace();
                    }
                }
            }
            return CONTINUE;
        }
    }

    class SearchSourceFiles extends SimpleFileVisitor<Path> {
        
        PathMatcher pathMatcher;
        
        Path location = null;
        
        public SearchSourceFiles(PathMatcher pathMatcher) {
            this.pathMatcher = pathMatcher;
        }
        
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attr) {
            
            if (attr.isRegularFile()) {
                if (pathMatcher.matches(file)) {
                    //System.out.println("Regular file: "+file);
                    
                    location = file;

                    return TERMINATE;
                }
            } else {
                //System.out.format("Other: %s ", file);
            }
            return CONTINUE;
        }
    }

}
