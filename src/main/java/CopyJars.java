import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;

/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
public class CopyJars {

    static MessageDigest md;

    static SourceMaker srcMaker;

    static Path eXist;
    static Path maven;

    /**
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws Exception {

        if (args.length < 3) {
            System.err.println(
                "need 3 parameters: \n" +
                " 1) eXist's source location;\n" +
                " 2) maven repo location;\n" +
                " 3) version;\n" +
                " 4) copy pom file from version (optional);"
            );
            System.exit(1);
        }

        md = MessageDigest.getInstance("SHA1");

        eXist = Paths.get(args[0]);
        maven = Paths.get(args[1]);

        String version = args[2];

        String fromVersion = null;
        if (args.length == 4) fromVersion = args[3];

        srcMaker = new SourceMaker(eXist);

        make(version, fromVersion, eXist.resolve("lib/extensions/exist-contentextraction.jar"), "existdb-contentextraction");
        make(version, fromVersion, eXist.resolve("exist.jar"), "existdb-core");
        make(version, fromVersion, eXist.resolve("lib/extensions/exist-index-lucene.jar"), "existdb-index-lucene");
        make(version, fromVersion, eXist.resolve("lib/extensions/exist-index-ngram.jar"), "existdb-index-ngram");
        make(version, fromVersion, eXist.resolve("lib/extensions/exist-index-range.jar"), "existdb-index-range");
        make(version, fromVersion, eXist.resolve("lib/extensions/exist-metadata-sleepycat.jar"), "existdb-metadata-berkeley");
        make(version, fromVersion, eXist.resolve("lib/extensions/exist-metadata-interface.jar"), "existdb-metadata-interface");
        make(version, fromVersion, eXist.resolve("exist-optional.jar"), "existdb-optional");

        make(version, fromVersion, eXist.resolve("lib/extensions/exist-security-ldap.jar"), "existdb-security-ldap");
//        make(version, fromVersion, eXist.resolve("lib/extensions/exist-security-saml.jar"), "existdb-security-saml");

        make(version, fromVersion, eXist.resolve("start.jar"), "existdb-start");

        make(version, fromVersion, eXist.resolve("lib/extensions/exist-versioning.jar"), "existdb-versioning");

        make(version, fromVersion, eXist.resolve("lib/extensions/exist-xslt.jar"), "existdb-xslt");

        make(version, fromVersion, eXist.resolve("lib/extensions/exist-modules.jar"), "existdb-xquery-modules");
    }

    private static void make(String version, String fromVersion, Path jar, String artifactId) throws IOException {

        Path folder = maven.resolve(artifactId).resolve(version);
        if (Files.notExists(folder)) Files.createDirectories(folder);

        Path pom = folder.resolve(artifactId + "-" + version+".pom");

        if (fromVersion != null) {
            if (Files.notExists(pom)) {

                Path original_pom = maven.resolve(artifactId).resolve(fromVersion).resolve(artifactId + "-" + fromVersion + ".pom");

                String dataPom = new String( Files.readAllBytes(original_pom) );

                dataPom = dataPom.replace("<version>"+fromVersion+"</version>", "<version>"+version+"</version>");

                Files.write(pom, dataPom.getBytes(), StandardOpenOption.CREATE);
            }
        }

        Path dist = folder.resolve(artifactId + "-" + version + ".jar");

        Files.copy(jar, dist, StandardCopyOption.REPLACE_EXISTING);

        Path src = srcMaker.process(eXist, dist);

        calcSHA(pom);
        calcSHA(dist);
        calcSHA(src);
        
        System.out.println(jar+" done");
    }

    private static void calcSHA(Path path) throws IOException {

        Path shaFile = path.getParent().resolve(path.getFileName() + ".sha1");

        try {
            Files.delete(shaFile);
        } catch (Exception e) {}

        md.reset();
        try (InputStream fis = Files.newInputStream(path)) {
            byte[] dataBytes = new byte[1024 * 1024 * 2]; //2M

            int nread = 0;
            while ((nread = fis.read(dataBytes)) != -1) {
                md.update(dataBytes, 0, nread);
            }

            byte[] mdbytes = md.digest();

            // convert the byte to hex format
            StringBuffer sb = new StringBuffer("");
            for (int i = 0; i < mdbytes.length; i++) {
                sb.append(Integer.toString((mdbytes[i] & 0xff) + 0x100, 16).substring(1));
            }
            
            Files.write(shaFile, sb.toString().getBytes());
        }
    }

}
