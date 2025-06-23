These documents are 1) background on the problem of ask-and-answer data
generation for fine-tuning LLMs 2) a set of prompts to an engineering expert
assistant 3) design documents and onboarding instructions 4) existing implementation Clojure code. The
prompts vary in their specificity and detail, but all work toward the same goal.
Pay particular attention to the onboarding instructions, as these are what you must follow.
You first task is to elucidate what that goal is, and outline the first few
steps in implementing that goal in an iterative, incremental fashion. Do not
generate detailed code, or any other artifacts than what I just specified.


---


Are you sure that this plan makes sense, given the current state of the code?
Perhaps the code already has done some of this? Realize that the sequence of
prompt documents was given to your predecessor, and it may have learned better
ways of approaching and solving the problem. Reflect on that and see if your
plan is optimal. Summarize your conclusions and the new plan, if it's different.
Then, before we proceed, let's reflect on the existing codebase and look for
ways to simplify the design. Some functions should be in a util namespace, and
the core module might be best split into more namespaces to separate concerns.
