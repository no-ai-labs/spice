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
      label: '🗺️ Roadmap & Migration',
      items: [
        'roadmap/overview',
        'roadmap/release-1-0-0',
        'roadmap/af-architecture',
        'roadmap/migration-guide',
        'roadmap/migration-0.5-to-0.6',
        'roadmap/hitl-design',
      ],
    },
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
        'core-concepts/modules',
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
        'dsl-guide/output-validation',
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
        'orchestration/swarm-strategies',
        'orchestration/multi-agent',
        'orchestration/tool-pipeline',
        'orchestration/agent-handoff',
        {
          type: 'category',
          label: '🕸️ Graph System (v0.7.0)',
          collapsed: false,
          items: [
            'orchestration/graph-system',
            'orchestration/graph-nodes',
            'orchestration/graph-middleware',
            'orchestration/graph-checkpoint',
            'orchestration/graph-validation',
            'orchestration/graph-hitl',
            'orchestration/parallel-execution',
          ],
        },
      ],
    },
    {
      type: 'category',
      label: '⚠️ Error Handling',
      items: [
        'error-handling/overview',
        'error-handling/spice-result',
        'error-handling/spice-error',
        'error-handling/context-patterns',
        'error-handling/inline-functions',
        'error-handling/best-practices',
      ],
    },
    {
      type: 'category',
      label: '🔧 Tools & Extensions',
      items: [
        'tools-extensions/creating-tools',
        'tools-extensions/tool-patterns',
        'tools-extensions/mcp',
        'tools-extensions/vector-stores',
      ],
    },
    {
      type: 'category',
      label: '🔌 Extensions',
      items: [
        'extensions/sparql-features',
      ],
    },
    {
      type: 'category',
      label: '⚡ Performance',
      items: [
        'performance/overview',
        'performance/tool-caching',
        'performance/cached-agent',
        'performance/batching-backend',
        'performance/production',
      ],
    },
    {
      type: 'category',
      label: '📊 Observability',
      items: [
        'observability/overview',
        'observability/setup',
        'observability/tracing',
        'observability/metrics',
        'observability/visualization',
      ],
    },
    {
      type: 'category',
      label: '📖 Guides',
      items: [
        'guides/feature-integration',
        'guides/execution-context-patterns',
        'guides/immutable-state-guide',
      ],
    },
    {
      type: 'category',
      label: '🧪 Testing',
      items: [
        'core-concepts/testing',
        'testing/context-testing',
      ],
    },
    {
      type: 'category',
      label: '🔒 Security',
      items: [
        'security/multi-tenancy',
      ],
    },
    {
      type: 'category',
      label: '🔥 Advanced',
      items: [
        'advanced/context-propagation',
        'guides/context-propagation',
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
        'examples/context-production',
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
        'api/node',
        'api/context',
        'api/execution-context',
        'api/registry',
        'api/dsl',
        'api/graph',
      ],
    },
  ],
};

export default sidebars;
