looking at the logic in the persistence module. and in the rpc gateway. I want to replace the authorization layer in the persistence module with something that will live in the gateway when receiving events. I want to support abac and basic rbac. and be able to use the json payload when evaluating policies.



I would like to have the policies defined in code similar to the EntityService, Policy, and Role decorators I have now. im not attached to the syntax but I do like the ease of use on the domain model. additionally I want to support abac for published services. this should also work with decorators or annotations for java code. you can find the existing implementation in kinotic-js and see some legacy docs in the webdocs folder. everything called structures is been moved into kinotic.



Also I would like the users of the system to be able to define access policies in the UI.




what can you recommend?