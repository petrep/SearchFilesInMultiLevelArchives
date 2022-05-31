package filemanipulations;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

//  TODO:   challenges:  what if my folder contains a zip file, that contains an lpkg file, that contains a folder that contains a jar file?
//                       in that case, how can I reference the file without using a path: the path would look like:
//       home/peter/myfolder/myzip.zip/mylpkg.lpkg/myfolder2/myjar.jar  --> this doesn't look like a path that I could reference to.

// Potential issues:  symbolic links;  file paths may be incorrect by nested archives;  linux file permission issues;
//  memory limitations when sub folder depth is too large;  performance issues;  broken zip archives;  hidden files;
//  Windows and Linux use different file separator;  how to handle extra large files like 5 GB files;
//  Zip files are not like folders, they contain entries instead of files, so the logic has to reflect that.
//  Feature ideas:  I could add an option to do a case sensitive search


public class SearchFilesInMultiLevelArchives {
    public static Set<String> searchResults = new HashSet<>();

    public static void main(String[] args) throws Exception {
        String rootFolder = "/home/peterpetrekanics/Downloads/canbedeleted/search";
        String searchFor = "ExportImportConstants";
        searchResults = walkFileTreeAndSearch(rootFolder, searchFor);

        for (String searchResult : searchResults) {
            System.out.println(" * " + searchResult + "\n");
        }
    }

    private static Set<String> walkFileTreeAndSearch(String rootFolder, String search) throws Exception {
        ArrayList<String> searchResults = new ArrayList<>();
        MyFileVisitor myFileVisitor = new MyFileVisitor(search);
        Files.walkFileTree(Paths.get(rootFolder), myFileVisitor);
        return myFileVisitor.searchResults;
    }
}

// There are multiple methods to override in SimpleFileVisitor
// We override only the visitFile method.
class MyFileVisitor extends SimpleFileVisitor<Path> {
    private String searched_expression;
    private ArrayList<String> filePaths = new ArrayList<>();

    public Set<String> searchResults = new HashSet<>();

    public MyFileVisitor(String s) {
        searched_expression = s;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        String fileSeparator = FileSystems.getDefault().getSeparator();
        String fileNameWithoutPath = file.getFileName().toString();
        System.out.println(" file: " + fileNameWithoutPath);
        filePaths.add(file.getFileName().toString());
//        System.out.println(" full file name: " + file.toString());
        String filePathWithoutFileName = file.toString().substring(0,file.toString().lastIndexOf(fileSeparator)+1);
        String filePathWithFileName = filePathWithoutFileName + fileNameWithoutPath;
        System.out.println(" filePathWithFileName: " + filePathWithFileName);
        if(fileNameWithoutPath.contains(searched_expression)) searchResults.add(filePathWithFileName);

        String filePath = file.toString();
        String extension = filePath.substring(filePath.lastIndexOf(".")+1);
//        System.out.println(" ext: " + extension);
        if(isValidArchiveExtension(extension)) {
            filePathWithoutFileName = filePathWithoutFileName + fileNameWithoutPath;

            try (FileInputStream fis = new FileInputStream(filePath);
                 BufferedInputStream bis = new BufferedInputStream(fis);
                 ZipInputStream zis = new ZipInputStream(bis)) {

                ZipEntry ze;

                while ((ze = zis.getNextEntry()) != null) {
                    if(!ze.isDirectory()) {
                        String zes = ze.getName();

                        String entryFileName = "";
                        String entryFilePath = "";
//                        System.out.println("zes: " + zes);
                        if(zes.contains(fileSeparator)) {
//                            entryFilePath = zes.substring(0, zes.lastIndexOf(fileSeparator));
                            entryFileName = zes.substring(zes.lastIndexOf(fileSeparator)+1);
                        } else {
                            entryFileName = zes;
                        }
                        System.out.println(" entryFileName: " + entryFileName);
                        entryFilePath = filePathWithoutFileName+fileSeparator+zes+fileSeparator;
                        System.out.println(" entryFilePath: " + entryFilePath);
                        if(entryFileName.contains(searched_expression)) searchResults.add(entryFilePath);

                        filePaths.add(entryFileName);

                        String entryExtension = entryFileName.substring(entryFileName.lastIndexOf(".")+1);
                        if(isValidArchiveExtension(entryExtension)) {
//                            System.out.println(" valid archive: " + entryFileName);
//                            System.out.println(" zesss: " + file);
                            check(zis, file+"/"+entryFileName, filePaths, searchResults, entryFilePath,searched_expression);
                        }

                    }
                }
            }

        } else if (file.toString().contains(searched_expression)) {
            System.out.println(" * FOUND: " + file.toString());
            searchResults.add(file.toString());
        }
        System.out.println("ending");
//        System.out.println(filePaths.toString());
        return FileVisitResult.CONTINUE;
    }

    public static void check(InputStream compressedInput, String name, ArrayList<String> filePaths, Set<String> searchResults, String entryFilePath, String searched_expression) throws IOException {
        ZipInputStream input = new ZipInputStream(compressedInput);
        ZipEntry entry = null;
        while ( (entry = input.getNextEntry()) != null ) {

            if(!entry.isDirectory()) {

                String zippedEntryName = entry.getName();
                //System.out.println("Found " + entry.getName() + " in " + name);
                String fileSeparator = FileSystems.getDefault().getSeparator();
                String zippedEntryFileName = "";
//                System.out.println("  zippedEntryName " + zippedEntryName);
                if(zippedEntryName.contains(fileSeparator)) {
                    zippedEntryFileName = zippedEntryName.substring(zippedEntryName.lastIndexOf(fileSeparator)+1);
                } else {
                    zippedEntryFileName = zippedEntryName;
                }
                System.out.println(" entry: " + zippedEntryFileName);
                System.out.println(" entrypath: " + entryFilePath + zippedEntryName);
                if(zippedEntryFileName.contains(searched_expression)) searchResults.add(entryFilePath + zippedEntryName);
                filePaths.add(zippedEntryFileName);
                String entryExtension = zippedEntryName.substring(zippedEntryName.lastIndexOf(".")+1);
                //System.out.println("entryExtension " + entryExtension);
                String origPath = entryFilePath + zippedEntryName + fileSeparator;
                if (isValidArchiveExtension(entryExtension)) {
                    check(input, name + "/" + zippedEntryName, filePaths, searchResults, origPath, searched_expression);
                }
            }
        }
    }

    private static boolean isValidArchiveExtension(String extension) {
        String[] validArchiveExtensions = new String[]{"lpkg", "zip", "war", "jar"};
        if(Arrays.asList(validArchiveExtensions).contains(extension)) return true;
        return false;
    }
}