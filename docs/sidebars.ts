import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

// This runs in Node.js - Don't use client-side code here (browser APIs, JSX...)

/**
 * Creating a sidebar enables you to:
 - create an ordered group of docs
 - render a sidebar for each doc of that group
 - provide next/previous navigation

 The sidebars can be generated from the filesystem, or explicitly defined here.

 Create as many sidebars as you want.
 */
const sidebars: SidebarsConfig = {
  // Main documentation sidebar
  docsSidebar: [
    'intro',
    {
      type: 'category',
      label: '🚀 Getting Started',
      items: [
        'getting-started/installation',
        'getting-started/first-agent',
        'getting-started/quick-start',
      ],
    },
    {
      type: 'category',
      label: '🏗️ Core Concepts',
      items: [
        'core-concepts/overview',
        'core-concepts/agent',
        'core-concepts/comm',
        'core-concepts/tool',
        'core-concepts/registry',
      ],
    },
    {
      type: 'category',
      label: '💻 DSL Guide',
      items: [
        'dsl-guide/overview',
        'dsl-guide/build-agent',
        'dsl-guide/build-flow',
        'dsl-guide/tools',
        'dsl-guide/vector-store',
      ],
    },
    {
      type: 'category',
      label: '🤖 LLM Integrations',
      items: [
        'llm-integrations/overview',
        'llm-integrations/openai',
        'llm-integrations/anthropic',
      ],
    },
    {
      type: 'category',
      label: '🌊 Agent Orchestration',
      items: [
        'orchestration/flows',
        'orchestration/swarm',
        'orchestration/multi-agent',
      ],
    },
    {
      type: 'category',
      label: '⚠️ Error Handling',
      items: [
        'error-handling/overview',
        'error-handling/spice-result',
        'error-handling/spice-error',
        'error-handling/best-practices',
      ],
    },
    {
      type: 'category',
      label: '🔧 Tools & Extensions',
      items: [
        'tools-extensions/creating-tools',
        'tools-extensions/mcp',
        'tools-extensions/vector-stores',
      ],
    },
    {
      type: 'category',
      label: '🌱 Spring Boot',
      items: [
        'spring-boot/overview',
        'spring-boot/configuration',
        'spring-boot/integration',
      ],
    },
    {
      type: 'category',
      label: '📊 Event Sourcing',
      items: [
        'event-sourcing/overview',
        'event-sourcing/getting-started',
        'event-sourcing/saga-pattern',
      ],
    },
    {
      type: 'category',
      label: '💡 Examples',
      items: [
        'examples/chatbot',
        'examples/multi-agent-system',
        'examples/rag-application',
      ],
    },
  ],

  // API Reference sidebar
  apiSidebar: [
    {
      type: 'category',
      label: '📚 API Reference',
      items: [
        'api/agent',
        'api/comm',
        'api/tool',
        'api/registry',
        'api/dsl',
      ],
    },
  ],
};

export default sidebars;
