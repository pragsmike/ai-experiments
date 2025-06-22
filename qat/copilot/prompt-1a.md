# Enhanced Multi-Agent Q&A Workflow for Interview Transcript Generation

You are an expert system architect, especially good for using LLMs as components.
Our task is to explore ways to support conversations among agents.

Below is an ambitious vision for a future system.
We will implement this in small steps.
The first step will give us a simple system consisting of two agents, the Question Generator Agent
and the Answer Agent.
The other roles will be added in later steps.
For now, we are interested in detailing the first step.
We will outline the later steps, but will not spend effort to detail them.

The accompanying document gives background on techniques for fine-tuning
models using ask-and-answer workflows.

We are exploring ways to enact multi-agent workflows using Clojure.
We will examine different coordination and communications mechanisms
afforded by the language, including core.async, futures, and so on.

The task we are automating is the generation of interview transcripts
resulting from ask-and-answer sequences between agents.

Given an article, we will generate Q&A sessions about the article content.
One agent reads the article and generates questions about it.
Another agent reads the article and answers questions about it.
This process is repeated several times, each time with a emphasis on a different aspect of the article.
The result is a set of interview transcripts.

The transcripts must be in JSONL format as recommended in the accompanying document.

## Objective
Implement a multi-agent system in Clojure that generates high-quality interview transcripts from articles using iterative question-answer sequences. The system will produce structured conversational data suitable for fine-tuning language models on domain-specific dialogue tasks.

## Multi-Agent Architecture

### Agent Roles and Specialization
- **Question Generator Agent**: Reads articles and generates contextually relevant questions with varying complexity levels
- **Answer Agent**: Provides comprehensive, accurate answers based on article content
- **Critic Agent**: Reviews Q&A pairs for quality, relevance, and factual accuracy
- **Moderator Agent**: Manages conversation flow, topic transitions, and ensures coherent multi-turn dialogues
- **Context Manager**: Maintains conversation state, tracks entity mentions, and manages multi-turn context retention

### Iterative Reasoning Framework
Implement an OODA-inspired loop (Observe-Orient-Decide-Act) for each agent:
1. **Observe**: Analyze current conversation state and article context
2. **Orient**: Determine optimal next action based on conversation goals
3. **Decide**: Select specific question type, answer approach, or critique focus
4. **Act**: Generate content and update conversation state

## Clojure Coordination Mechanisms

### Core.async Implementation
```clojure
;; Channel-based agent communication
(def question-channel (chan 10))
(def answer-channel (chan 10))
(def critique-channel (chan 10))
(def transcript-channel (chan 50))

;; Agent coordination pipeline
(go-loop []
  (let [article (<! article-input-channel)
        questions (<! (generate-questions article))
        answers (<! (generate-answers article questions))
        critiqued-qa (<! (critique-qa answers))
        transcript (<! (format-transcript critiqued-qa))]
    (>! transcript-channel transcript)
    (recur)))
```

### Futures for Parallel Processing
- Use futures for concurrent question generation across different article aspects
- Implement timeout mechanisms for agent responses
- Handle parallel critique and quality assessment

### Agent State Management
- Implement shared state using atoms for conversation context
- Use refs with STM for coordinated multi-agent state updates
- Maintain conversation memory across iterative cycles

## Multi-Perspective Iteration Strategy

### Aspect-Focused Iterations
For each article, generate multiple Q&A sessions focusing on:
1. **Factual Content**: Direct information extraction and comprehension
2. **Analytical Reasoning**: Inference, implications, and logical connections
3. **Critical Evaluation**: Strengths, weaknesses, and alternative perspectives
4. **Contextual Applications**: Real-world relevance and practical implications
5. **Comparative Analysis**: Connections to broader domain knowledge

### Multiagent Debate Enhancement
- Implement agent disagreement and resolution mechanisms
- Generate diverse answer candidates through multiple answer agents
- Use critic agents to facilitate constructive debate and refinement

## Output Format Specification

### JSONL Structure for Multi-Turn Conversations
```json
{
  "conversation_id": "article_123_session_2",
  "article_metadata": {
    "title": "Article Title",
    "domain": "finance|healthcare|legal|technology",
    "source": "publication_name",
    "length": 2500
  },
  "session_metadata": {
    "focus_aspect": "analytical_reasoning",
    "iteration_number": 2,
    "agent_versions": {"questioner": "v1.2", "answerer": "v1.1", "critic": "v1.0"}
  },
  "messages": [
    {"role": "system", "content": "You are analyzing a financial regulation article. Focus on analytical reasoning and implications."},
    {"role": "user", "content": "What are the primary regulatory changes proposed in this article?"},
    {"role": "assistant", "content": "The article proposes three main regulatory changes..."},
    {"role": "user", "content": "How might these changes impact small financial institutions differently than large ones?"},
    {"role": "assistant", "content": "The differential impact would likely manifest in several ways..."}
  ],
  "quality_metrics": {
    "coherence_score": 0.89,
    "factual_accuracy": 0.94,
    "conversation_depth": 4,
    "topic_coverage": 0.87
  }
}
```

### Data Quality Assurance
- Implement automated quality scoring using coherence and relevance metrics
- Include conversation depth tracking (number of meaningful turns)
- Add topic coverage assessment to ensure comprehensive article exploration
- Generate conversation summaries for quick quality review

## Advanced Features

### Context Retention Mechanisms
- Implement entity tracking across conversation turns
- Maintain topic coherence through explicit conversation state management
- Support graceful topic transitions and conversation closure

### Domain Adaptation Support
- Configure agent prompts and reasoning patterns for specific domains
- Implement domain-specific vocabulary and concept emphasis
- Support custom evaluation criteria per domain

### Scalability Considerations
- Design for processing large article collections in parallel
- Implement efficient conversation deduplication
- Support incremental processing and resume capabilities

## Quality Metrics and Evaluation

### Automated Assessment
- Conversation coherence scoring
- Factual accuracy validation against source article
- Question diversity and complexity measurement
- Answer completeness and relevance evaluation

### Human Review Integration
- Sample-based human evaluation workflows
- Quality threshold enforcement with human-in-the-loop validation
- Continuous improvement based on evaluation feedback

## Expected Outcomes

This enhanced workflow will produce:
- **High-quality conversational datasets** suitable for domain-specific fine-tuning
- **Diverse multi-turn dialogues** that capture expert-level reasoning patterns
- **Scalable transcript generation** from large article collections
- **Structured data** optimized for modern fine-tuning frameworks (Hugging Face, OpenAI API, Together AI)

The resulting transcripts will enable fine-tuning of conversational models that demonstrate improved domain expertise, multi-turn reasoning capabilities, and natural dialogue flow.
