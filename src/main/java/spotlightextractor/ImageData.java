package spotlightextractor;

import java.text.Normalizer;

public class ImageData {

    private String id;
    private String url;
    private String description;

    public ImageData() {
    }

    public ImageData(String id, String url, String description) {
        this.id = id;
        this.url = url;
        this.description = normalizeDescription(description);
    }

    private String normalizeDescription(String description) {
        String normalize = Normalizer.normalize(description, Normalizer.Form.NFD);
        String removeAccent = normalize.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return removeAccent;
    }

    public String getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return "ImageData{" +
                "id='" + id + '\'' +
                ", url='" + url + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
