package com.amine.questionservice.service;

import com.amine.questionservice.dao.QuestionDao;
import com.amine.questionservice.exception.ResourceNotFoundException;
import com.amine.questionservice.model.Question;
import com.amine.questionservice.model.QuestionWrapper;
import com.amine.questionservice.model.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class QuestionService {

    @Autowired
    QuestionDao questionDao;

    public ResponseEntity<List<Question>> getAllQuestions() {
        return new ResponseEntity<>(questionDao.findAll(), HttpStatus.OK);
    }

    public ResponseEntity<List<Question>> getQuestionsByCategory(String category) {
        return new ResponseEntity<>(questionDao.findByCategory(category), HttpStatus.OK);
    }

    public ResponseEntity<String> addQuestion(Question question) {
        questionDao.save(question);
        return new ResponseEntity<>("Question added successfully", HttpStatus.CREATED);
    }

    public ResponseEntity<List<Integer>> getQuestionsForQuiz(String categoryName, Integer numQuestions) {
        List<Integer> questions = questionDao.findRandomQuestionsByCategory(categoryName, numQuestions);
        return new ResponseEntity<>(questions, HttpStatus.OK);
    }

    public ResponseEntity<List<QuestionWrapper>> getQuestionsFromId(List<Integer> questionIds) {
        List<QuestionWrapper> wrappers = new ArrayList<>();

        for (Integer id : questionIds) {
            Optional<Question> questionOpt = questionDao.findById(id);
            if (questionOpt.isPresent()) {
                Question question = questionOpt.get();
                QuestionWrapper wrapper = new QuestionWrapper();
                wrapper.setId(question.getId());
                wrapper.setQuestionTitle(question.getQuestionTitle());
                wrapper.setOption1(question.getOption1());
                wrapper.setOption2(question.getOption2());
                wrapper.setOption3(question.getOption3());
                wrapper.setOption4(question.getOption4());
                wrappers.add(wrapper);
            }
        }

        return new ResponseEntity<>(wrappers, HttpStatus.OK);
    }

    public ResponseEntity<Integer> getScore(List<Response> responses) {
        int right = 0;

        for (Response response : responses) {
            Optional<Question> questionOpt = questionDao.findById(response.getId());
            if (questionOpt.isPresent()) {
                Question question = questionOpt.get();
                if (response.getResponse() != null && response.getResponse().equalsIgnoreCase(question.getRightAnswer())) {
                    right++;
                }
            }
        }
        return new ResponseEntity<>(right, HttpStatus.OK);
    }
}
