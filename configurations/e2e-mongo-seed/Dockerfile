FROM mongo

COPY e2euser.json /e2euser.json
CMD mongoimport --uri="mongodb://mongo:27017/otp_middleware" --collection=AdminUser /e2euser.json