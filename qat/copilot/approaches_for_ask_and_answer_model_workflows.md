## Approaches for "Ask-and-Answer" Model Workflows

You want to automate a workflow where:

- Model 1 reads an article and generates questions about it.
- Model 2 reads the same article and answers those questions.

Several modern tools can help you build this, including n8n and LangFlow. Hereâ€™s a breakdown of how each fits your needs and what complementary tech you might consider.

---

## LangFlow: Visual LLM Pipelines for Question Generation \& Answering

**LangFlow** is built on top of LangChain and excels at visually creating and chaining LLM-powered workflows, such as:

- Ingesting documents (articles)
- Generating questions (using prompt templates and LLMs)
- Answering questions (contextual Q\&A using the same or another LLM)

**How youâ€™d use LangFlow:**

- Load your article using a File or Document Loader component.
- Use an LLM node with a prompt template to generate questions about the article.
- Use another LLM node (potentially with a different prompt or model) to answer those questions, referencing the same article context.
- You can chain these steps visually, and LangFlow supports multi-agent flows, memory, and prompt engineering[^1_1][^1_2].

**Strengths:**

- Rapid prototyping of multi-agent LLM chains.
- Visual, no-code/low-code interface.
- Good for chaining prompts, agents, and context-aware tasks[^1_1][^1_3][^1_2].

**Limitations:**

- Not designed for complex API orchestration or enterprise workflow automation (e.g., integrating with external systems or triggers)[^1_3].

---

## n8n: Workflow Automation with LLM Integration

**n8n** is a general-purpose workflow automation tool with strong support for LLMs and over 400 integrations[^1_4][^1_3][^1_5]. It can:

- Orchestrate multi-step processes (trigger, process, store, notify, etc.).
- Use AI Agent nodes to connect to LLMs (OpenAI, etc.) for both question generation and answering[^1_4][^1_5].
- Integrate with databases, APIs, chatbots, and more.

**How youâ€™d use n8n:**

- Trigger the workflow (e.g., when a new article is uploaded).
- Use an AI Agent node with a prompt to generate questions from the article.
- Pass the article and generated questions to a second AI Agent node (or a different model) to answer them.
- Store or route the results as needed.

**Strengths:**

- Powerful for orchestrating end-to-end workflows, including external integrations and data flows.
- Can trigger and manage LLM tasks, store outputs, and integrate with enterprise systems[^1_3][^1_5].

**Limitations:**

- Not as specialized for chaining complex, context-aware LLM tasks as LangFlow or LangChain[^1_3].

---

## Which Should You Use?

| Use Case | LangFlow | n8n |
| :-- | :-- | :-- |
| Visual chaining of LLM tasks | Excellent | Basic (via nodes) |
| Multi-agent, context-aware pipelines | Excellent | Possible, but less ergonomic |
| API/data integration, automation | Limited | Excellent (400+ integrations) |
| Rapid prototyping of LLM logic | Excellent | Good, but less specialized |
| Orchestrating external systems | Limited | Excellent |

**Best Practice:**

- Use **LangFlow** to build and prototype your LLM question generation and answering chains.
- Use **n8n** to trigger those LangFlow pipelines, handle file uploads, store results, or integrate with other systems[^1_3].

---

## Other Options

- **Haystack**: A Python framework for building custom QA pipelines, including retrieval-augmented generation (RAG). Excellent for code-first setups and supports both question generation and answering in a modular way[^1_6].
- **LangChain**: The underlying library powering LangFlow, suitable for code-first users who want maximum flexibility.

---

## Example Workflow in LangFlow

1. **File Loader**: Load the article.
2. **Prompt Node**: "Generate five insightful questions about this article."
3. **LLM Node**: Outputs questions.
4. **Prompt Node**: "Given the article and the following question, provide an answer."
5. **LLM Node**: Answers each question using the article as context.
6. **Output Node**: Collect and display/store Q\&A pairs[^1_1][^1_2].

---

## Example Workflow in n8n

1. **Trigger**: New article uploaded.
2. **AI Agent Node**: Prompt to generate questions.
3. **AI Agent Node**: For each question, prompt to answer using the article.
4. **Data Storage/Notification**: Save or send the Q\&A results[^1_4][^1_5].

---

## Summary

- **LangFlow** is ideal for visually chaining LLMs for question generation and answering with shared context.
- **n8n** is best for orchestrating the workflow, integrating with external systems, and managing automation.
- For your use case, consider building the LLM logic in LangFlow and orchestrating with n8n if you need automation or integration with other tools[^1_3].

You can combine both for maximum flexibility and power in your "ask-and-answer" workflow.

<div style="text-align: center">â‚</div>

[^1_1]: https://www.cohorte.co/blog/langflow-a-visual-guide-to-building-llm-apps-with-langchain

[^1_2]: https://docs.langflow.org/document-qa

[^1_3]: https://www.linkedin.com/posts/sanjoyghosh82_langflow-n8n-agenticai-activity-7324973196280012801-T3RK

[^1_4]: https://docs.n8n.io/advanced-ai/intro-tutorial/

[^1_5]: https://www.yugabyte.com/blog/ai-workflows-using-n8n-and-yugabytedb/

[^1_6]: https://haystack.deepset.ai/tutorials/27_first_rag_pipeline

[^1_7]: https://dl.acm.org/doi/10.5555/AAI29186286

[^1_8]: https://www.microsoft.com/en-us/research/blog/joint-model-question-answering-question-generation/

[^1_9]: https://www.edutopia.org/article/how-generative-ai-can-support-nonfiction-reading/

[^1_10]: https://deepai.org/publication/asking-questions-the-human-way-scalable-question-answer-generation-from-text-corpus

[^1_11]: https://community.n8n.io/t/n8n-workflow-with-jenkins-and-ai-agent/95565

[^1_12]: https://smythos.com/ai-agents/comparison/n8n-vs-langflow/

[^1_13]: https://www.edutopia.org/article/using-technology-reading-instruction-6-tips-teachers/

[^1_14]: https://www.readingrockets.org/topics/ed-tech-and-digital-media/articles/using-multimedia-support-reading-instruction

[^1_15]: http://www.3plearning.com/blog/connectingsamrmodel/

[^1_16]: https://www.beanstack.com/blog/how-to-enhance-reading-instruction-with-technology

[^1_17]: https://www.youtube.com/watch?v=eosgj7sZT14

[^1_18]: https://n8n.io/workflows/categories/ai/

[^1_19]: https://www.youtube.com/watch?v=mQt1hOjBH9o

[^1_20]: https://www.reddit.com/r/n8n/comments/1l9qs8f/i_built_an_aipowered_pdf_analysis_pipeline_that/


---

# The purpose of the question-answer sequence is to iterate it several times to produce a conversation for fine-tuning over the article.  This would be done with a large number of articles.  Does that sound workable?  How could it be made better?

Your proposed workflowâ€”iteratively generating question-answer (QA) conversations over many articles to create fine-tuning data for conversational modelsâ€”is not only workable but aligns with several current best practices in LLM development and research. Hereâ€™s an analysis and suggestions to make it even more effective:

## Workability of Iterative QA Generation

- **Iterative QA Generation**: Using one model to generate questions and another to answer, then repeating this process to simulate multi-turn conversations, is a proven way to build high-quality conversational datasets for fine-tuning. This approach helps models learn context retention, reasoning, and dialogue coherence over multiple turns[^2_1][^2_2][^2_3].
- **Scale and Automation**: With a large number of articles, this process can be automated using workflow tools (like n8n or LangFlow) and scaled to generate diverse, multi-turn dialogues for robust model training[^2_4][^2_1].


## How to Make It Better

### 1. Multi-Agent and Debate Frameworks

- **Multiagent Debate**: Instead of just two models, consider a multiagent setup where several â€œgenerationâ€ agents produce diverse answers and â€œcriticâ€ agents review, debate, and refine them. This iterative, debate-based process leads to higher-quality and more diverse training data, and models can specialize in different conversational roles[^2_2].
- **Role Specialization**: Assign different agents to specialize in question generation, answering, critique, or summarization. This promotes diversity and depth in the generated conversations[^2_2].


### 2. Iterative Reasoning and Planning

- **Iterative Reasoning Loops**: Incorporate reasoning frameworks like the OODA (Observe-Orient-Decide-Act) loop, where the system observes context, orients itself, decides on a response, and actsâ€”then repeats. This approach has been shown to improve answer quality, especially for complex, multi-turn tasks[^2_5][^2_6].
- **Planning and Answering Stages**: Use a two-stage process where the model first plans (sets the topic or intent for the next turn) and then answers, iteratively alternating to maintain focus and coherence over long dialogues[^2_7].


### 3. Data Quality and Structure

- **Structured Data**: Ensure your data is well-organized: include clear turn-by-turn records, explicit context windows, and metadata (e.g., article source, turn index)[^2_4][^2_3].
- **Diversity and Coverage**: Use articles from a wide range of topics and domains to improve generalization. Vary question complexity and conversational styles[^2_8].
- **Evaluation and Filtering**: Regularly evaluate generated conversations for coherence, relevance, and factuality. Use both automated metrics (F1, BLEU, ROUGE, etc.) and human review to filter out low-quality samples[^2_1][^2_8].


### 4. Fine-Tuning Best Practices

- **Sequential Instruction Tuning**: If your conversations involve multi-step instructions or reasoning, consider sequential instruction tuningâ€”training the model to follow a series of instructions within a single dialogue turn[^2_9].
- **Parameter Optimization**: Experiment with learning rates, batch sizes, and layer freezing to balance adaptation and generalization[^2_10][^2_11].
- **Regular Iteration**: Continuously refine your process based on validation results, adjusting prompts, agent roles, and fine-tuning parameters[^2_11].


### 5. Advanced Conversation Management

- **Multi-Turn Context Retention**: Implement memory management and entity tracking so the model can reference earlier turns and maintain topic continuity[^2_3][^2_1].
- **Topic Shift and Dialogue Flow**: Teach the model to manage topic transitions, summarize prior turns, and close topics gracefully for more natural conversations[^2_3].

---

## Summary Table: Enhancements for Iterative QA Fine-Tuning

| Enhancement | Description | Benefit |
| :-- | :-- | :-- |
| Multiagent Debate | Multiple agents generate, critique, and refine responses | Higher-quality, diverse data |
| Iterative Reasoning Loops | OODA or similar frameworks for multi-step reasoning | Improved coherence, complex QA handling |
| Structured Data \& Diversity | Well-organized, varied datasets | Better generalization |
| Sequential Instruction Tuning | Training on multi-step conversational instructions | Enhanced multi-turn reasoning |
| Context Retention Strategies | Memory, entity tracking, topic management | More natural, coherent conversations |
| Rigorous Evaluation | Automated and human review, metric tracking | Ensures data quality |


---

## Conclusion

Your approach is solid and aligns with state-of-the-art methods for building conversational fine-tuning datasets. To maximize effectiveness:

- Use multiagent, iterative frameworks for richer data.
- Structure and diversify your dataset.
- Employ advanced conversation management and evaluation techniques.
- Iterate on your process with regular validation and refinement.

These enhancements will help you produce high-quality, multi-turn conversational data for robust LLM fine-tuning at scale[^2_2][^2_1][^2_6].

<div style="text-align: center">â‚</div>

[^2_1]: https://www.together.ai/blog/fine-tuning-llms-for-multi-turn-conversations-a-technical-deep-dive

[^2_2]: https://bdtechtalks.com/2025/01/27/llm-multiagent-fine-tuning/

[^2_3]: https://whitebeardstrategies.com/blog/10-best-techniques-for-conversational-ai-fine-tuning/

[^2_4]: https://github.com/PratishYadav/StructuredQA-Model

[^2_5]: https://arxiv.org/html/2404.11792v1

[^2_6]: https://www.themoonlight.io/en/review/enhancing-qa-with-domain-specific-fine-tuning-and-iterative-reasoning-a-comparative-study

[^2_7]: https://aclanthology.org/2024.emnlp-main.270.pdf

[^2_8]: https://www.restack.io/p/fine-tuning-answering-models-cat-ai

[^2_9]: https://arxiv.org/html/2403.07794v1

[^2_10]: https://www.turing.com/resources/finetuning-large-language-models

[^2_11]: https://www.datacamp.com/tutorial/fine-tuning-large-language-models

[^2_12]: https://neurips.cc/virtual/2024/101689

[^2_13]: https://docs.together.ai/docs/iterative-workflow

[^2_14]: https://heidloff.net/article/fine-tuning-question-answering/

[^2_15]: https://pub.aimind.so/step-by-step-guide-to-fine-tuning-models-for-extractive-question-answering-68ae57c46596?gi=6beaeb87c1f3

[^2_16]: https://github.com/VKT2003/Fine-Tune-Question-Answering-dataset

[^2_17]: https://www.youtube.com/watch?v=cWaJurbtY30

[^2_18]: https://arxiv.org/html/2502.05449v2


---

# How might domain-specific fine-tuning and iterative reasoning complement each other in my workflow

Domain-specific fine-tuning and iterative reasoning are highly complementary in a workflow designed to generate high-quality, multi-turn conversational data over articles for fine-tuning language models.

## How They Complement Each Other

**Domain-Specific Fine-Tuning**

- Fine-tuning a model on data from a particular domain (e.g., finance, medicine, law) enables it to internalize the specialized vocabulary, reasoning patterns, and contextual nuances unique to that field[^3_1][^3_2][^3_3].
- This adaptation leads to improved retrieval accuracy (when fine-tuning embedding models) and more relevant, context-aware answers (when fine-tuning generative models)[^3_1][^3_4][^3_3].
- For example, a model fine-tuned on SEC filings will better understand regulatory language and financial concepts, resulting in more precise Q\&A performance[^3_1][^3_4][^3_3].

**Iterative Reasoning**

- Iterative reasoning mechanisms, such as the OODA (Observe, Orient, Decide, Act) loop, allow the model to refine its responses over multiple steps, breaking down complex queries into manageable parts and incorporating feedback at each stage[^3_1][^3_4][^3_3].
- This approach improves the modelâ€™s ability to handle multi-hop reasoning, maintain context across turns, and approach human-expert quality in its answers[^3_1][^3_4][^3_3].
- Iterative reasoning is especially valuable in domains where questions require synthesis, clarification, or stepwise deductionâ€”capabilities that are enhanced when the model is already aligned with domain-specific logic[^3_1][^3_4][^3_3].


## Practical Synergy in Your Workflow

- **Initial Fine-Tuning**: By first fine-tuning your models on domain-specific data, you ensure that both the question generator and answerer are equipped with the right context and knowledge for your articlesâ€™ subject matter.
- **Iterative QA Generation**: As you iterate question-answer sequences, iterative reasoning lets the answerer model revisit the article, clarify ambiguities, and refine its responses, producing richer, more realistic conversational data for fine-tuning[^3_1][^3_4][^3_3].
- **Improved Data Quality**: This combination results in Q\&A pairs (and multi-turn dialogues) that are not only accurate and contextually appropriate but also reflect the reasoning depth and conversational flow found in expert human interactions[^3_1][^3_4][^3_3].


## How to Make the Most of Both

- Fine-tune both your embedding/retrieval and generative models on your target domain to maximize the relevance of both retrieved context and generated answers[^3_1][^3_4][^3_3].
- Implement iterative reasoning loops in your answer generation process, allowing the model to review and improve its answers over several cycles before finalizing each response[^3_1][^3_4][^3_3].
- Use the outputs of this combined process as high-quality training data for further fine-tuning, ensuring your conversational model learns both domain knowledge and advanced reasoning strategies.


## Summary Table

| Technique | Main Benefit | How It Helps the Workflow |
| :-- | :-- | :-- |
| Domain-Specific Fine-Tuning | Embeds specialized knowledge and reasoning | Produces contextually accurate Q\&A |
| Iterative Reasoning | Refines responses, handles complexity, multi-hop | Generates deeper, more realistic conversations |
| Combined | Human-expert-level conversational QA | High-quality, nuanced fine-tuning data |

**In short:**
Domain-specific fine-tuning gives your models the right knowledge and reasoning patterns, while iterative reasoning lets them apply that knowledge in a stepwise, expert-like fashion. Together, they enable your workflow to create superior conversational datasets for fine-tuning, especially when scaling across large article collections[^3_1][^3_4][^3_3].

<div style="text-align: center">â‚</div>

[^3_1]: https://arxiv.org/abs/2404.11792

[^3_2]: https://www.linkedin.com/pulse/fine-tuning-advanced-reasoning-models-methodologies-33-ramachandran-rn8re

[^3_3]: https://www.themoonlight.io/en/review/enhancing-qa-with-domain-specific-fine-tuning-and-iterative-reasoning-a-comparative-study

[^3_4]: https://arxiv.org/html/2404.11792v1

[^3_5]: https://www.aimodels.fyi/papers/arxiv/enhancing-qanda-domain-specific-fine-tuning-iterative

[^3_6]: https://dzone.com/articles/llm-fine-tuning-strategies-for-domain-specific-app

[^3_7]: https://www.nature.com/articles/s41524-025-01564-y

[^3_8]: https://thealliance.ai/blog/advancing-domain-specific-qa-the-ai-alliances-guide

[^3_9]: https://www.rohan-paul.com/p/domain-specific-llm-fine-tuning

[^3_10]: https://paperswithcode.com/paper/openrft-adapting-reasoning-foundation-model


---

# What format must the interview transcripts from the Q\&A sessions be for easiest use in fine-tuning?  What tools are recommended for this sort of fine-tuning, and what sort of models give best results?

To maximize the effectiveness and ease of fine-tuning with your Q\&A interview transcripts, careful attention to data format, tool selection, and model choice is essential. Hereâ€™s a comprehensive guide based on current best practices and recent advancements:

## 1. Optimal Transcript Format for Fine-Tuning

**a. Structure and Consistency**

- Use a consistent, structured format across all transcripts. The most widely accepted and model-friendly format for conversational fine-tuning is a JSONL (JSON Lines) file, where each line represents a conversation as a list of messages with explicit roles and content[^4_1][^4_2][^4_3].
- For multi-turn conversations, structure each sample as an ordered sequence of turns, with each turn labeled by role (e.g., "system", "user", "assistant") and containing the corresponding text[^4_2][^4_3].

**Example (for multi-turn dialogue):**

```json
{
  "messages": [
    {"role": "system", "content": "You are a helpful assistant. Answer questions based on the article provided."},
    {"role": "user", "content": "What is the main argument of the article?"},
    {"role": "assistant", "content": "The main argument is that..."},
    {"role": "user", "content": "Can you provide supporting evidence?"},
    {"role": "assistant", "content": "Certainly, the article states..."}
  ]
}
```

- For single-turn Q\&A, use prompt-completion pairs:

```json
{"prompt": "<question>", "completion": "<answer>"}
```

- Include metadata (e.g., article title, domain, timestamp) if it aids downstream analysis or filtering[^4_4][^4_5].

**b. Speaker Labels and Timestamps**

- Label each speaker clearly (e.g., "user", "assistant"), and optionally include timestamps for traceability and quality control[^4_5][^4_6].
- Ensure clarity in formatting: break up dialogue into manageable turns, use proper punctuation, and avoid large text blocks[^4_5][^4_6].

**c. Data Cleaning and Anonymization**

- Remove duplicates, correct errors, and anonymize sensitive information to protect privacy and ensure data quality[^4_4][^4_7].


## 2. Recommended Tools for Fine-Tuning

**a. Data Preparation \& Annotation**

- **Labellerr, Kili, Labelbox, Label Studio:** These platforms streamline annotation, support collaborative workflows, and ensure high-quality, well-labeled conversational data[^4_8].
- **Python (pandas, regex):** For cleaning, formatting, and anonymization tasks[^4_4].

**b. Fine-Tuning Frameworks**

- **Hugging Face Transformers:** The industry standard for open-source LLM fine-tuning, supporting conversational and instruction-following models[^4_9][^4_10][^4_11].
- **OpenAI Fine-Tuning API:** For fine-tuning GPT-3.5/4 models on prompt-completion pairs or conversational data[^4_1][^4_12].
- **Together AI, Databricks:** For scalable, cloud-based fine-tuning and deployment[^4_9][^4_2].
- **Custom scripts:** For advanced or highly customized workflows, especially when integrating with proprietary data pipelines.


## 3. Best Model Types for Results

**a. Conversational LLMs**

- Choose models pre-trained or instruction-tuned for dialogue, such as Llama-3-Chat, GPT-3.5/4 (OpenAI), Claude, or open-source alternatives like ChatQA or fine-tuned GPT-2/3 derivatives[^4_11][^4_3].
- For domain-specific tasks, start with a strong conversational base model and fine-tune it on your Q\&A transcripts for best results[^4_13][^4_2][^4_3].

**b. Retrieval-Augmented Generation (RAG)**

- For large corpora or when context windows are limited, consider RAG architectures that combine retrieval of relevant passages with generative answering. These models excel at grounding responses in source material and scale well for document-based Q\&A[^4_14][^4_3].

**c. Parameter-Efficient Tuning**

- For smaller datasets or resource constraints, explore LoRA, adapters, or selective prompt tuning (SPT) for efficient adaptation without full model retraining[^4_15][^4_16].

---

## Summary Table

| Aspect | Recommendation |
| :-- | :-- |
| Transcript Format | JSONL with structured "messages" (role/content), clear speaker labels, optional metadata[^4_1][^4_2][^4_3] |
| Data Tools | Labellerr, Kili, Labelbox, Label Studio, Python (pandas, regex) |
| Fine-Tuning Frameworks | Hugging Face Transformers, OpenAI Fine-Tuning API, Together AI, Databricks[^4_9][^4_8][^4_2] |
| Model Types | Conversational LLMs (Llama-3-Chat, GPT-3.5/4, Claude, ChatQA), RAG models, parameter-efficient tuning[^4_11][^4_3][^4_14] |


---

## Key Best Practices

- **Consistency:** Ensure all transcripts follow the same structured format for seamless ingestion by fine-tuning frameworks[^4_5][^4_6].
- **Quality:** Clean, anonymize, and diversify your data to maximize model performance[^4_7].
- **Model Selection:** Start with a conversational, instruction-tuned base model for multi-turn Q\&A. For large-scale or document-grounded Q\&A, consider RAG or hybrid retrieval-generation approaches[^4_14][^4_3].
- **Tooling:** Use annotation and data-labeling tools for efficient, high-quality dataset creation, and leverage established fine-tuning frameworks for training and deployment[^4_9][^4_8][^4_2].

By following these guidelines, youâ€™ll ensure your Q\&A transcripts are in the optimal format, use the best tools for the job, and select models that deliver robust, domain-adapted conversational performance.


[^4_1]: https://blog.spheron.network/a-guide-to-fine-tuning-gpt-for-enhanced-conversational-performance

[^4_2]: https://www.together.ai/blog/fine-tuning-llms-for-multi-turn-conversations-a-technical-deep-dive

[^4_3]: https://arxiv.org/html/2401.10225

[^4_4]: https://quizgecko.com/learn/fine-tuning-llms-with-conversation-data-mhm51l

[^4_5]: https://insight7.io/how-to-analyze-interview-transcripts-with-ai-tools/

[^4_6]: https://www.noota.io/en/interview-transcript-guide

[^4_7]: https://whitebeardstrategies.com/blog/techniques-for-fine-tuning-conversational-ai-systems/

[^4_8]: https://www.labellerr.com/blog/best-chatbot-fine-tuning-tools-3/

[^4_9]: https://www.lakera.ai/blog/llm-fine-tuning-guide

[^4_10]: https://discuss.huggingface.co/t/fine-tuning-a-conversational-model/80003

[^4_11]: https://huggingface.co/bniladridas/conversational-ai-fine-tuned

[^4_12]: https://insight7.io/top-5-ai-tools-to-analyze-interview-transcripts-in-2025/

[^4_13]: https://insight7.io/training-llms-on-domain-specific-transcripts-for-better-accuracy/

[^4_14]: https://arxiv.org/html/2402.01722v1

[^4_15]: https://arxiv.org/html/2406.18187

[^4_16]: https://www.reddit.com/r/LocalLLaMA/comments/16v4f8i/interact_with_llm_based_on_finetuned_interviews/

[^4_17]: https://community.openai.com/t/how-to-improve-my-article-from-transcript-prompt/45410

[^4_18]: https://www.youtube.com/watch?v=U3WYdVf959c

[^4_19]: https://www.truefoundry.com/blog/top-tools-for-fine-tuning

[^4_20]: https://www.youtube.com/watch?v=shkMOHwJ4SM

[^4_21]: https://www.reddit.com/r/LocalLLaMA/comments/1g2vhy3/creating_very_highquality_transcripts_with/

[^4_22]: https://community.openai.com/t/finetuning-gpt3-5-with-a-conversation/329409

[^4_23]: https://www.reddit.com/r/LocalLLaMA/comments/14vnfh2/my_experience_on_starting_with_fine_tuning_llms/

[^4_24]: https://arxiv.org/html/2408.13296v1

[^4_25]: https://www.labellerr.com/blog/best-chatbot-fine-tuning-tools/

[^4_26]: https://www.reddit.com/r/LLMDevs/comments/1hn7lpr/finetuning_an_llm_on_a_huge_conversation_dataset/
