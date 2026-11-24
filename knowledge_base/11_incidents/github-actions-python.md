---
doc_id: NT-070
title: "Runbook: Python not found в GitHub Actions"
category: 11_incidents
audience: "ENGINEERING,QA,DEVOPS"
access_level: EMPLOYEE
owner: "Developer Enablement"
status: Published
version: 1.0
last_reviewed: 2026-10-01
review_due: 2027-01-01
language: ru
tags: "incidents, engineering,qa,devops, developer-enablement"
---

# Runbook: Python not found в GitHub Actions

> **NovaTech / FirstDay AI — учебная демонстрационная документация.** Компания, системы, процедуры и сроки вымышлены и не являются правилами реального работодателя.

## Назначение и контекст

Ошибка python.exe not found на self-hosted runner связана с окружением процесса и PATH. Для GitHub-hosted runner рекомендуется actions/setup-python, а локальное приложение localhost требует запуска в самой CI среде.

## Для кого эта инструкция

**Аудитория:** ENGINEERING,QA,DEVOPS. **Владелец:** Developer Enablement. **Уровень доступа:** EMPLOYEE. **Статус:** Published.

## Порядок действий

1. Уточните self-hosted или GitHub-hosted runner.
2. Для hosted добавьте actions/setup-python.
3. Проверьте python --version после setup.
4. Убедитесь в наличии зависимостей и тестового сервиса.
5. Просмотрите первый упавший шаг.

## Типичные ошибки и способы решения

- **Ситуация 1.** Не считайте, что Python из IDE доступен runner.
- **Ситуация 2.** Не подключайте runner к неизвестным pull requests.

## Вопросы и ответы

**Вопрос:** Что делает setup-python?

**Ответ:** Настраивает выбранную версию Python для workflow.

**Вопрос:** Почему localhost не виден на hosted runner?

**Ответ:** Он относится к машине runner, а не к пользовательскому ПК.

## Контрольный результат

После выполнения инструкции сотрудник должен уметь объяснить, зачем нужен описанный процесс, показать выполненные действия или заявку в учебной системе Nimbus, а при блокере — указать ответственного владельца. Если шаг невыполним, его следует отметить как заблокированный, а не как завершённый.

## Как поддерживать актуальность

Уточнения и исправления передаются через заявку **Knowledge Feedback** в Nimbus, обязательно с названием документа **NT-070**, описанием противоречия и предложением исправления. Сотрудник не должен менять утверждённые политики только на основании ответа AI.
