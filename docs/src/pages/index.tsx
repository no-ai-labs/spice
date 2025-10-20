import type {ReactNode} from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import Heading from '@theme/Heading';

import styles from './index.module.css';

function HomepageHeader() {
  const {siteConfig} = useDocusaurusContext();
  return (
    <header className={clsx('hero hero--primary', styles.heroBanner)}>
      <div className="container">
        <Heading as="h1" className="hero__title">
          {siteConfig.title}
        </Heading>
        <p className="hero__subtitle">{siteConfig.tagline}</p>
        <div className={styles.buttons}>
          <Link
            className="button button--secondary button--lg"
            to="/docs/intro">
            Get Started 🚀
          </Link>
          <Link
            className="button button--outline button--lg margin-left--md"
            to="/docs/getting-started/installation">
            Quick Start ⏱️
          </Link>
        </div>
      </div>
    </header>
  );
}

function FeatureCard({icon, title, description}: {icon: string; title: string; description: string}) {
  return (
    <div className="col col--4 margin-bottom--lg">
      <div className="card">
        <div className="card__header">
          <div style={{fontSize: '2.5rem', marginBottom: '0.5rem'}}>{icon}</div>
          <h3>{title}</h3>
        </div>
        <div className="card__body">
          <p>{description}</p>
        </div>
      </div>
    </div>
  );
}

function FeaturesSection() {
  const features = [
    {
      icon: '🚀',
      title: 'Simple yet Powerful',
      description: 'Get started in minutes, scale to complex multi-agent systems'
    },
    {
      icon: '🛡️',
      title: 'Type-Safe',
      description: "Leverage Kotlin's type system for compile-time safety"
    },
    {
      icon: '⚡',
      title: 'Async-First',
      description: 'Built on coroutines for efficient concurrent operations'
    },
    {
      icon: '✨',
      title: 'Clean DSL',
      description: 'Intuitive API that reads like natural language'
    },
    {
      icon: '🔌',
      title: 'Extensible',
      description: 'Easy to add custom agents, tools, and integrations'
    },
    {
      icon: '🤖',
      title: 'Multi-LLM Support',
      description: 'OpenAI, Anthropic, Google Vertex AI, and more'
    }
  ];

  return (
    <section className={clsx('margin-top--lg margin-bottom--xl', styles.features)}>
      <div className="container">
        <div className="text--center margin-bottom--lg">
          <Heading as="h2">Why Spice? 🌶️</Heading>
        </div>
        <div className="row">
          {features.map((props, idx) => (
            <FeatureCard key={idx} {...props} />
          ))}
        </div>
      </div>
    </section>
  );
}

function QuickStartSection() {
  return (
    <section className={clsx('margin-top--xl margin-bottom--xl', styles.quickStart)}>
      <div className="container">
        <div className="text--center margin-bottom--lg">
          <Heading as="h2">Quick Start</Heading>
          <p className="hero__subtitle">Create your first agent in seconds</p>
        </div>
        <div className="row">
          <div className="col col--10 col--offset-1">
            <div className="card">
              <div className="card__body">
                <pre>
                  <code className="language-kotlin">{`// Create your first agent
val assistant = buildAgent {
    id = "assistant-1"
    name = "AI Assistant"
    description = "A helpful AI assistant"

    // Add tools
    tool("greet") {
        description = "Greet someone"
        parameter("name", "string", "Person's name")
        execute { params ->
            ToolResult.success("Hello, \${params["name"]}! 👋")
        }
    }

    // Handle messages
    handle { comm ->
        SpiceResult.success(
            comm.reply("How can I help you today?", id)
        )
    }
}`}</code>
                </pre>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

function CTASection() {
  return (
    <section className={clsx('hero hero--secondary', styles.cta)}>
      <div className="container text--center">
        <Heading as="h2">Ready to spice up your AI applications? 🌶️</Heading>
        <p className="hero__subtitle margin-bottom--lg">
          Join the growing community of developers building with Spice Framework
        </p>
        <div className={styles.buttons}>
          <Link
            className="button button--primary button--lg"
            to="/docs/getting-started/installation">
            Get Started
          </Link>
          <Link
            className="button button--secondary button--lg margin-left--md"
            to="/docs/examples/chatbot">
            View Examples
          </Link>
        </div>
      </div>
    </section>
  );
}

export default function Home(): ReactNode {
  const {siteConfig} = useDocusaurusContext();
  return (
    <Layout
      title="Home"
      description="Modern Multi-LLM Orchestration Framework for Kotlin 🌶️">
      <HomepageHeader />
      <main>
        <FeaturesSection />
        <QuickStartSection />
        <CTASection />
      </main>
    </Layout>
  );
}
