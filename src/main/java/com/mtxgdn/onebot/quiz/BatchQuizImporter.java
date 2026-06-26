package com.mtxgdn.onebot.quiz;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class BatchQuizImporter {

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final String DATA_PATH = "data" + File.separator + "quiz" + File.separator + "questions.json";
    private static final String RESOURCE_PATH = "/data/quiz/questions.json";

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("用法: java -cp <classpath> com.mtxgdn.onebot.quiz.BatchQuizImporter <CSV文件路径>");
            System.out.println("CSV格式: 答案序号(1-4),题目");
            System.out.println("示例: 4,电影《肖申克的救赎》中典狱长最后的结局是？");
            System.exit(1);
        }

        String csvPath = args[0];
        File csvFile = new File(csvPath);
        
        if (!csvFile.exists()) {
            System.err.println("错误: 文件不存在 - " + csvPath);
            System.exit(1);
        }

        try {
            List<QuizQuestion> newQuestions = parseCsv(csvFile);
            List<QuizQuestion> existingQuestions = loadExistingQuestions();
            
            int maxId = existingQuestions.stream()
                .mapToInt(QuizQuestion::getId)
                .max()
                .orElse(0);

            int added = 0;
            for (QuizQuestion q : newQuestions) {
                q.setId(++maxId);
                existingQuestions.add(q);
                added++;
            }

            saveQuestions(existingQuestions);
            System.out.println("成功导入 " + added + " 道题目，当前题库共 " + existingQuestions.size() + " 道");

        } catch (Exception e) {
            System.err.println("导入失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static List<QuizQuestion> parseCsv(File csvFile) throws IOException {
        List<QuizQuestion> questions = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8))) {
            
            String line;
            int lineNum = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                String[] parts = line.split(",", 2);
                if (parts.length != 2) {
                    System.err.println("警告: 第 " + lineNum + " 行格式错误，跳过: " + line);
                    continue;
                }

                try {
                    int answer = Integer.parseInt(parts[0].trim());
                    if (answer < 1 || answer > 4) {
                        System.err.println("警告: 第 " + lineNum + " 行答案序号无效(" + answer + ")，跳过");
                        continue;
                    }

                    String question = parts[1].trim();
                    if (question.isEmpty()) {
                        System.err.println("警告: 第 " + lineNum + " 行题干为空，跳过");
                        continue;
                    }

                    questions.add(new QuizQuestion(0, question, answer));
                } catch (NumberFormatException e) {
                    System.err.println("警告: 第 " + lineNum + " 行答案必须是数字，跳过: " + line);
                }
            }
        }

        return questions;
    }

    private static List<QuizQuestion> loadExistingQuestions() {
        File file = new File(DATA_PATH);
        
        if (file.exists()) {
            try (Reader reader = new FileReader(file, StandardCharsets.UTF_8)) {
                QuizQuestion[] arr = gson.fromJson(reader, QuizQuestion[].class);
                if (arr != null) {
                    List<QuizQuestion> list = new ArrayList<>(List.of(arr));
                    System.out.println("已读取本地题库文件，共 " + list.size() + " 道题目");
                    return list;
                }
            } catch (Exception e) {
                System.err.println("加载本地题库失败，尝试读取内置题库: " + e.getMessage());
            }
        }

        try (InputStream is = BatchQuizImporter.class.getResourceAsStream(RESOURCE_PATH)) {
            if (is != null) {
                try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    QuizQuestion[] arr = gson.fromJson(reader, QuizQuestion[].class);
                    if (arr != null) {
                        List<QuizQuestion> list = new ArrayList<>(List.of(arr));
                        System.out.println("已读取内置题库资源，共 " + list.size() + " 道题目");
                        return list;
                    }
                }
            } else {
                System.err.println("未找到内置题库资源");
            }
        } catch (Exception e) {
            System.err.println("加载内置题库失败: " + e.getMessage());
        }

        System.out.println("未找到现有题库，将创建新题库");
        return new ArrayList<>();
    }

    private static void saveQuestions(List<QuizQuestion> questions) throws IOException {
        File dir = new File("data" + File.separator + "quiz");
        if (!dir.exists()) {
            dir.mkdirs();
        }

        try (Writer writer = new FileWriter(DATA_PATH, StandardCharsets.UTF_8)) {
            gson.toJson(questions, writer);
        }
    }
}
