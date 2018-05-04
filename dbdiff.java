import static java.lang.System.*;
import java.lang.reflect.*;
import java.util.*;
import java.sql.*;
import java.io.*;
import java.nio.file.*;
import java.net.*;

class dbdiff {

    private void tests() {
        assert true;
    }

    private static String head(String[] arr) {
        return arr != null && arr.length > 0 ? arr[0] : null;
    }

    private static String[] tail(String[] arr) {
        return arr != null && arr.length > 1 ? Arrays.copyOfRange(arr, 1, arr.length) : new String[]{};
    }


    private static void emit(String pattern, Object...args) {
        err.println(String.format(pattern, args));
    }

    private static int call(String command, String[] args) {
        try {
            Method m = dbdiff.class.getDeclaredMethod(command, String[].class);
            return (int) m.invoke(null, new Object[]{ args });
        } catch (Exception e) {
            return 1;
        }
    }

    private static String readFile(String filename) throws IOException {
        return new String(Files.readAllBytes(Paths.get(filename)));
    }

    private static void loadJar(String jar) throws Exception {
        URL url = new File(jar).toURI().toURL();
        URLClassLoader classLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
        Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
        method.setAccessible(true);
        method.invoke(classLoader, url);
    }

    private static int sql(String...args) throws Exception {
        String query = readFile(head(tail(args)));
        ResourceBundle p = ResourceBundle.getBundle(head(args));
        String jar = p.getString("driver_jar");
        if (jar != null) {
            loadJar(jar);
        }
        try (
         Connection c = DriverManager.getConnection(
           p.getString("url"),
           p.getString("username"),
           p.getString("password"));
         Statement s = c.createStatement();
         ResultSet rs = s.executeQuery(query)) {
            ResultSetMetaData rsmd = rs.getMetaData();
            for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                emit("%s %s %s nullable=%s", rsmd.getColumnName(i), rsmd.getColumnTypeName(i), rsmd.getColumnClassName(i), rsmd.isNullable(i));
            }
            while (rs.next()) {
                emit("begin row");
                for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                    emit("\t%s = [%s]", rsmd.getColumnName(i), rs.getObject(i));
                }
                emit("end row");
            }
        }
        return 0;
    }

    private static List<String> getResourceFiles(String path, String extension) throws IOException {
        List<String> filenames = new ArrayList<>();
        try(
         InputStream in = getResourceAsStream(path);
         BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
            String resource;
            while((resource = br.readLine()) != null) {
                if (resource.endsWith(extension)) {
                    filenames.add(resource);
                }
            }
        }
        return filenames;
    }

    private static InputStream getResourceAsStream(String resource) {
        final InputStream in = getContextClassLoader().getResourceAsStream(resource);
        return in == null ? dbdiff.class.getResourceAsStream(resource) : in;
    }

    private static ClassLoader getContextClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    private static void usage() throws Exception {
        emit("dbdiff <command> <options>");
        emit("supported commands:");
        emit("");
        emit("\tsql: executes a sql query and prints the result.");
        emit("");
        emit("\tdbdiff sql <connection> <sql file>");
        emit("");
        emit("\t\twhere <connection> is the base name of a properties file available within the classpath.");
        emit("");
        emit("\t\t\tjava.class.path = %s", System.getProperty("java.class.path"));
        emit("");
        emit("\t\tand available properties files are:");
        emit("");
        getResourceFiles("/", ".properties").stream().forEach(f -> emit("\t\t\t%s", f));
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
            exit(1);
        }
        int exit_code = call(head(args), tail(args));
        if (exit_code != 0) {
            usage();
        }
        exit(exit_code);
    }
}
