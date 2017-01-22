package com.stirante.updater;

import com.stirante.updater.utils.AsyncTask;
import com.stirante.updater.utils.ConfigLoader;
import com.stirante.updater.utils.HashUtil;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Created by stirante
 */
public class Main extends Application {

    private static String pathToCheck;
    @FXML
    private Label status;
    @FXML
    private ProgressBar progress;
    private HashMap<String, String> config;
    private HashMap<String, String> defs;

    public static void main(String[] args) {
        if (args.length > 0) {
            if (args.length == 3) {
                if (args[0].equalsIgnoreCase("generate")) {
                    generateDefinitions(args[1], args[2]);
                    System.out.println("Finished");
                    System.exit(0);
                }
            } else if (args.length == 2) {
                if (args[0].equalsIgnoreCase("check")) {
                    pathToCheck = args[1];
                    launch(args);
                    return;
                }
            }
            System.out.println("Invalid arguments!");
            System.out.println("Usage: java -jar Updater.jar generate <directory> <definition file>");
            System.out.println("Usage: java -jar Updater.jar check <directory>");
        }
    }

    private static void generateDefinitions(String path, String definitionPath) {
        LinkedList<Definition> defs = new LinkedList<>();
        File dir = new File(path);
        if (!dir.exists()) {
            System.out.println("Directory does not exist!");
            return;
        }
        if (!dir.isDirectory()) {
            System.out.println("This is not a directory!");
            return;
        }
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                generateDefinition(defs, dir.getAbsolutePath(), file);
            }
        }
        File f = new File(definitionPath);
        try {
            f.createNewFile();
            PrintWriter writer = new PrintWriter(new FileWriter(f));
            for (Definition def : defs) {
                writer.println(def.path + "=" + def.hash);
            }
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void generateDefinition(List<Definition> defs, String originalPath, File f) {
        if (f.isDirectory()) {
            File[] files = f.listFiles();
            if (files != null) {
                for (File file : files) {
                    generateDefinition(defs, originalPath, file);
                }
            }
            return;
        }
        System.out.println("Generating definition for " + f);
        String hash = HashUtil.fileHash(f);
        String path = f.getAbsolutePath().replace(originalPath, "").replaceAll("\\\\", "/").substring(1).replaceAll(" ", "%20");
        defs.add(new Definition(path, hash));
    }

    @Override
    public void start(Stage stage) throws Exception {
        config = ConfigLoader.loadConfig();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/Main.fxml"));
        loader.setController(this);
        VBox root = loader.load();
        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.show();
        status.setText("Downloading definitions");
        progress.progressProperty().setValue(0);
        new DownloadDefinitionTask().execute(config.get("definition"));
    }

    private static class Definition {
        private String path;
        private String hash;

        private Definition(String path, String hash) {
            this.path = path;
            this.hash = hash;
        }
    }

    private class DownloadDefinitionTask extends AsyncTask<String, Double, byte[]> {
        @Override
        public byte[] doInBackground(String[] params) {
            InputStream input = null;
            ByteArrayOutputStream output = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(params[0]);
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    return null;
                }
                int fileLength = connection.getContentLength();

                input = connection.getInputStream();
                output = new ByteArrayOutputStream();

                byte data[] = new byte[4096];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    if (isCancelled()) {
                        input.close();
                        return null;
                    }
                    total += count;
                    if (fileLength > 0)
                        publishProgress((double) total / (double) fileLength);
                    output.write(data, 0, count);
                }
            } catch (Exception e) {
                return null;
            } finally {
                try {
                    if (output != null)
                        output.close();
                    if (input != null)
                        input.close();
                } catch (IOException ignored) {
                }

                if (connection != null)
                    connection.disconnect();
            }
            byte[] bytes = output.toByteArray();
            try {
                output.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return bytes;
        }

        @Override
        public void onProgress(Double progress) {
            Main.this.progress.progressProperty().setValue(progress);
        }

        @Override
        public void onPostExecute(byte[] result) {
            defs = ConfigLoader.load(new ByteArrayInputStream(result));
            status.setText("Checking definitions");
            progress.progressProperty().setValue(0);
            new CheckDefinitionTask().execute();
        }
    }

    private class CheckDefinitionTask extends AsyncTask<Void, Double, Map<String, String>> {
        @Override
        public Map<String, String> doInBackground(Void[] params) {
            File root = new File(pathToCheck);
            HashMap<String, String> toUpdate = new HashMap<>();
            int i = 0;
            for (String s : defs.keySet()) {
                i++;
                File f = new File(root.getAbsolutePath() + File.separator + s);
                String localHash = HashUtil.fileHash(f);
                if (!localHash.equals(defs.get(s))) {
                    toUpdate.put(f.getAbsolutePath(), s);
                }
                publishProgress((double) (i / defs.size()));
            }
            return toUpdate;
        }

        @Override
        public void onProgress(Double progress) {
            Main.this.progress.progressProperty().setValue(progress);
        }

        @Override
        public void onPostExecute(Map<String, String> result) {
            if (result.isEmpty()) {
                status.setText("Finished");
                progress.progressProperty().setValue(1);
                new CloseTask().execute();
            } else {
                status.setText("Downloading files");
                progress.progressProperty().setValue(0);
                new DownloadTask().execute(result);
            }
        }
    }

    private class DownloadTask extends AsyncTask<Map<String, String>, Double, Void> {
        @Override
        public Void doInBackground(Map<String, String>[] params) {
            for (Map.Entry<String, String> entry : params[0].entrySet()) {
                File file = new File(entry.getKey());
                String remotePath = config.get("base") + entry.getValue();
                download(file, remotePath);
            }
            return null;
        }

        private void download(File file, String remote) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("Downloading " + remote + " to " + file.getAbsolutePath());
            Platform.runLater(() -> status.setText("Downloading " + file.getName()));
            InputStream input = null;
            FileOutputStream output = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(remote);
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    return;
                }
                int fileLength = connection.getContentLength();

                input = connection.getInputStream();
                output = new FileOutputStream(file);

                byte data[] = new byte[32768];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    if (isCancelled()) {
                        input.close();
                    }
                    total += count;
                    if (fileLength > 0)
                        publishProgress((double) total / (double) fileLength);
                    output.write(data, 0, count);
                }
            } catch (Exception ignored) {
            } finally {
                try {
                    if (output != null)
                        output.close();
                    if (input != null)
                        input.close();
                } catch (IOException ignored) {
                }

                if (connection != null)
                    connection.disconnect();
            }
            try {
                output.flush();
                output.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onProgress(Double progress) {
            Main.this.progress.progressProperty().setValue(progress);
        }

        @Override
        public void onPostExecute(Void result) {
            status.setText("Finished");
            progress.progressProperty().setValue(1);
            new CloseTask().execute();
        }
    }

    private class CloseTask extends AsyncTask<Void, Void, Void> {
        @Override
        public Void doInBackground(Void[] params) {
            String action = config.get("extra_action");
            try {
                Runtime.getRuntime().exec(action, null, new File(pathToCheck));
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public void onPostExecute(Void result) {
            Platform.exit();
        }
    }
}
