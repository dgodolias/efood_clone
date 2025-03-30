import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class FilterStores {
    public static void main(String[] args) {
        String inputFilePath = "data/stores.json";
        String outputFilePath = "data/filtered_stores.json";

        List<Store> filteredStores = filterStores(inputFilePath, "pizzeria", 4, "$$");
        writeFilteredStoresToFile(filteredStores, outputFilePath);
    }

    private static List<Store> filterStores(String filePath, String category, int minStars, String priceCategory) {
        List<Store> filteredStores = new ArrayList<>();
        try (FileReader reader = new FileReader(filePath)) {
            Gson gson = new Gson();
            Type storeListType = new TypeToken<List<Store>>() {}.getType();
            List<Store> stores = gson.fromJson(reader, storeListType);

            for (Store store : stores) {
                if (store.getFoodCategory().equalsIgnoreCase(category) &&
                        store.getStars() >= minStars &&
                        store.getPriceCategory().equals(priceCategory)) {
                    filteredStores.add(store);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return filteredStores;
    }

    private static void writeFilteredStoresToFile(List<Store> stores, String filePath) {
        try (FileWriter writer = new FileWriter(filePath)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(stores, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
