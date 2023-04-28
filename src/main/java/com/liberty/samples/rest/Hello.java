package com.liberty.samples.rest;

import java.io.IOException;
import java.net.Inet4Address;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.text.SimpleDateFormat;
import java.util.Date;

@WebServlet(urlPatterns = "/hello")
public class Hello extends HttpServlet {
        private static final long serialVersionUID = 1L;
        public static final SimpleDateFormat dtFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                resp.getWriter().append("Hello from " + Inet4Address.getLocalHost().getHostName() + " [built-on: "+dtFormatter.format(new Date())+"]");
        }
}
