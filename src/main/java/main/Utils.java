package main;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

public class Utils {
    public static int generateNonZeroInteger(Random rnd, int bound) {
        int res;
        do {
            res = rnd.nextInt(bound);
        } while(res == 0);
        return res;
    }

    public static <T> T fromFile(String path, Class<T> valueType) {
        ObjectMapper mapper = JsonMapper.builder()
                .findAndAddModules()
                .build();
        try {
            return mapper.readValue(new File(path), valueType);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void toFile(String path, Object toWrite) {
        ObjectMapper mapper = JsonMapper.builder()
                .findAndAddModules()
                .build();
        try {
            File f = new File(path);
            if (!f.getParentFile().exists()) {
                f.getParentFile().mkdirs();
            }
            String s = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(toWrite);
            FileWriter fw = new FileWriter(path);
            fw.write(s);
            fw.close();
        } catch (IOException e1) {
            e1.printStackTrace();
        }
    }
}
