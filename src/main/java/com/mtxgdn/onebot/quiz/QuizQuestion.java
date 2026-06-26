package com.mtxgdn.onebot.quiz;

public class QuizQuestion {

    private int id;
    private String question;
    private int answer;

    public QuizQuestion() {
    }

    public QuizQuestion(int id, String question, int answer) {
        this.id = id;
        this.question = question;
        this.answer = answer;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public int getAnswer() {
        return answer;
    }

    public void setAnswer(int answer) {
        this.answer = answer;
    }
}
