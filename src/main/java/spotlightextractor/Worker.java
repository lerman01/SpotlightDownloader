package spotlightextractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;


public class Worker implements Runnable {

    private static final Logger logger = LogManager.getLogger(Worker.class);

    private static final String URL = "https://arc.msn.com/v3/Delivery/Cache?pid=%s&ctry=%s&lc=en&fmt=json&lo=%s";
    private static final List<String> PID_LIST = Arrays.asList("209567", "279978", "209562");
    private static final List<String> COUNTRIES_LIST = Arrays.asList("en", "de", "us");
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/75.0.3770.100 Safari/537.36";
    private static final Random randomGenerator = new Random();

    private static ConcurrentHashMap<String, ImageData> imagesList;
    private CloseableHttpClient httpclient;

    Worker(CloseableHttpClient httpclient) {

        this.httpclient = httpclient;
    }

    public static ConcurrentHashMap<String, ImageData> getImagesList() {
        return imagesList;
    }

    public static void setImagesList(ConcurrentHashMap<String, ImageData> oldImagesNames) {
        imagesList = oldImagesNames;
    }

    public void run() {
        fetchData();
    }

    private void fetchData() {
        for (String country : COUNTRIES_LIST) {
            try {
                ImageData imageData = fetchImageData(country);
                if (!isImagesExist(imageData)) {
                    File imageFile = fetchImage(imageData);
                } else {
                    logger.debug(String.format("File already exists : %s", imageData));
                }
            } catch (Exception e) {
                logger.error("Error while get image", e);
            }
        }
    }

    private ImageData fetchImageData(String country) {
        CloseableHttpResponse response = null;
        String imageUrl = null;
        String imageDescription = null;
        String imageId = null;
        HttpEntity entity1 = null;
        String body = null;
        try {
            HttpGet httpGet = new HttpGet(String.format(URL, PID_LIST.get(randomGenerator.nextInt(PID_LIST.size())), country, randomGenerator.nextInt(900000) + 100000));
            httpGet.setHeader(HttpHeaders.USER_AGENT, USER_AGENT);
            response = httpclient.execute(httpGet);
            if (response.getStatusLine().getStatusCode() != 200) {
                logger.error(String.format("Fail to retrive new image data: %s", response.getStatusLine()));
            }
            entity1 = response.getEntity();
            body = IOUtils.toString(entity1.getContent(), "UTF-8");
            ObjectMapper objectMapper = new ObjectMapper();
            String item = objectMapper.readTree(body).get("batchrsp").get("items").get(0).get("item").asText();
            JsonNode jsonNode = objectMapper.readTree(item);
            imageUrl = jsonNode.get("ad").get("image_fullscreen_001_landscape").get("u").asText();
            if (imageUrl.lastIndexOf("?") != -1) {
                imageId = imageUrl.substring(imageUrl.lastIndexOf("/") + 1, imageUrl.lastIndexOf("?"));
            } else {
                imageId = imageUrl.substring(imageUrl.lastIndexOf("/") + 1);
            }
            jsonNode = jsonNode.get("ad").get("title_text");
            if (jsonNode != null) {
                imageDescription = jsonNode.get("tx").asText();
            } else {
                imageDescription = imageId;
            }
        } catch (IOException e) {
            logger.error(String.format("Error while fetching image data", e));
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (IOException e) {
                    logger.error(String.format("Error while close response: %s", response.getStatusLine()));
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
            File imageFile = getNextFilename(imageData.getDescription());
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
                    logger.error(String.format("Error while try close response: %s", response.getStatusLine()));
                }
            }
        }
    }

    private synchronized boolean isImagesExist(ImageData imageData) {
        if (imagesList.get(imageData.getId()) == null) {
            imagesList.put(imageData.getId(), imageData);
            return false;
        } else {
            return true;
        }
    }

    private File getNextFilename(String filename) {
        File file = new File("images/" + filename + ".jpg");
        Integer fileNo = 1;
        while (file.exists()) {
            file = new File("images/" + filename + fileNo.toString() + ".jpg");
            fileNo++;
        }
        return file;
    }
}

