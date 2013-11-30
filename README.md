This project takes the raw screen scraping from the re:Invent 2013 session catalog website and creates
an index of session, title, speakers, abstract, links to YouTube and SlideShare.

You need to get an API access key for SlideShare for this to work.  Once you have one edit config.groovy.

Run with ./gradlew run > out.html

For now, I have checked in the code to JSlideShare into this github project.  Unfortunately the version
on google code doesn't seem to updated recently.  I'm doing to see if there is some way to get my patches
into it which consist of using the now required HTTPS and adding a creation date to Slideshare info.
