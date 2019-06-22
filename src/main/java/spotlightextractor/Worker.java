package spotlightextractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;


public class Worker implements Runnable {

    private static final Logger logger = LogManager.getLogger(Worker.class);

    private static final String URL = "https://arc.msn.com/v3/Delivery/Cache?pid=%s&fmt=json&rafb=0&ua=WindowsShellClient&lc=en-US&pl=en-US&ctry=neutral";
    private static final List<String> PID_LIST = Arrays.asList("209567", "279978", "209562");
    private static final Random randomGenerator = new Random();
    private static final String IGNORED_ERROR = "Warning: [minor] Excessive number of items";

    private static ConcurrentHashMap<String, String> fetchedImages;
    private CloseableHttpClient httpclient;

    Worker(CloseableHttpClient httpclient) {
        this.httpclient = httpclient;
    }

    public static void setFetchedImages(ConcurrentHashMap<String, String> oldFetchedImages) {
        fetchedImages = new ConcurrentHashMap<>();
        for (String imageId : oldFetchedImages.keySet()) {
            fetchedImages.put(imageId, oldFetchedImages.get(imageId));
        }
    }

    public static ConcurrentHashMap<String, String> getFetchedImages() {
        return fetchedImages;
    }

    public void run() {
        fetchData();
    }

    private void fetchData() {
        try {
            ImageData imageData = fetchImageData();
            if (!isFileExist(imageData.getId())) {
                File imageFile = fetchImage(imageData);
                saveIdToMetadata(imageData, imageFile);
            } else {
                logger.debug(String.format("File already exists : %s", imageData));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private ImageData fetchImageData() {
        CloseableHttpResponse response = null;
        String imageUrl = null;
        String imageDescription = null;
        String imageId = null;
        try {
            HttpGet httpGet = new HttpGet(String.format(URL, PID_LIST.get(randomGenerator.nextInt(PID_LIST.size()))));
            response = httpclient.execute(httpGet);
            if (response.getStatusLine().getStatusCode() != 200) {
                logger.error(String.format("Fail to retrive new image data: %s", response.getStatusLine()));
            }
            HttpEntity entity1 = response.getEntity();
            String body = IOUtils.toString(entity1.getContent(), "UTF-8");
            ObjectMapper objectMapper = new ObjectMapper();
            String item = objectMapper.readTree(body).get("batchrsp").get("items").get(0).get("item").asText();
            JsonNode jsonNode = objectMapper.readTree(item);
            imageUrl = jsonNode.get("ad").get("image_fullscreen_001_landscape").get("u").asText();
            imageDescription = jsonNode.get("ad").get("title_text").get("tx").asText();
            if (imageUrl.lastIndexOf("?") != -1) {
                imageId = imageUrl.substring(imageUrl.lastIndexOf("/") + 1, imageUrl.lastIndexOf("?"));
            } else {
                imageId = imageUrl.substring(imageUrl.lastIndexOf("/") + 1);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return new ImageData(imageId, imageUrl, imageDescription);
    }

    private File fetchImage(ImageData imageData) throws Exception {
        CloseableHttpResponse response = null;
        try {
            HttpGet httpGet = new HttpGet(imageData.getUrl());
            response = httpclient.execute(httpGet);
            if (response.getStatusLine().getStatusCode() != 200) {
                logger.error(String.format("Fail to retrive image content: %s", response.getStatusLine()));
            }
            HttpEntity entity = response.getEntity();
            InputStream is = entity.getContent();
            String filePath = "images/" + imageData.getDescription() + ".jpg";
            File imageFile = new File(filePath);
            FileOutputStream fos = new FileOutputStream(imageFile);
            int inByte;
            while ((inByte = is.read()) != -1)
                fos.write(inByte);
            is.close();
            fos.close();
            return imageFile;
        } catch (Exception e) {
            throw e;
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void saveIdToMetadata(ImageData imageData, File imageFile) throws Exception {
        Process process = new ProcessBuilder(Main.EXIF_TOOL_PATH, "-title=" + imageData.getId(), "-overwrite_original", "-charset", "filename=latin", "images\\" + imageFile.getName()).start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        StringBuilder builder = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
            builder.append(System.getProperty("line.separator"));
        }
        String error = builder.toString();
        if (error.length() > 0) {
            if (!error.startsWith(IGNORED_ERROR)) {
                logger.error(String.format("Error while save metadata: %s", error));
            } else {
                logger.debug(error);
            }
        }
        fetchedImages.put(imageData.getId(), imageFile.getName());
    }

    private boolean isFileExist(String imageId) {
        if (fetchedImages.get(imageId) == null) {
            return false;
        } else {
            return true;
        }
    }
}

