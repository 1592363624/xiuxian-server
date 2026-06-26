package com.mtxgdn.onebot.quiz;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class QuizDataCleaner {

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("用法: java -cp <classpath> com.mtxgdn.onebot.quiz.QuizDataCleaner <输入文件> <输出文件>");
            System.exit(1);
        }

        String inputPath = args[0];
        String outputPath = args[1];

        try {
            List<QuizQuestion> questions = loadQuestions(inputPath);
            saveQuestionsWithoutOptions(questions, outputPath);
            System.out.println("处理完成！原文件 " + questions.size() + " 道题目已保存到 " + outputPath);
        } catch (Exception e) {
            System.err.println("处理失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static List<QuizQuestion> loadQuestions(String path) throws IOException {
        List<QuizQuestion> list = new ArrayList<>();
        
        try (Reader reader = new FileReader(path, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (element.isJsonArray()) {
                JsonArray array = element.getAsJsonArray();
                for (JsonElement item : array) {
                    if (item.isJsonObject()) {
                        JsonObject obj = item.getAsJsonObject();
                        int id = obj.has("id") ? obj.get("id").getAsInt() : 0;
                        String question = obj.has("question") ? obj.get("question").getAsString() : "";
                        int answer = obj.has("answer") ? obj.get("answer").getAsInt() : 0;
                        list.add(new QuizQuestion(id, question, answer));
                    }
                }
            }
        }
        
        return list;
    }

    private static void saveQuestionsWithoutOptions(List<QuizQuestion> questions, String path) throws IOException {
        File file = new File(path);
        File dir = file.getParentFile();
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (Writer writer = new FileWriter(path, StandardCharsets.UTF_8)) {
            gson.toJson(questions, writer);
        }
    }
}
