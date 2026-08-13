package com.bengu.springblog.exceptions;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice(annotations = Controller.class)
public class GlobalExceptionHandler {



    @ExceptionHandler(UsernameAlreadyExistsException.class)
    public String handleUsernameAlreadyExists(
            UsernameAlreadyExistsException exception,
            HttpServletRequest request,
            Model model
    ) {
        model.addAttribute(
                "suggestedUsername",
                request.getParameter("username")
        );

        model.addAttribute(
                "usernameError",
                exception.getMessage()
        );

        return "choose-username";
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public String handleEmailAlreadyExists(
            EmailAlreadyExistsException exception,
            Model model
    ) {
        model.addAttribute(
                "registrationError",
                exception.getMessage()
        );

        return "choose-username";
    }

    @ExceptionHandler(GoogleAccountAlreadyLinkedException.class)
    public String handleGoogleAccountAlreadyLinked(
            GoogleAccountAlreadyLinkedException exception,
            Model model
    ) {
        model.addAttribute(
                "registrationError",
                exception.getMessage()
        );

        return "choose-username";
    }
}